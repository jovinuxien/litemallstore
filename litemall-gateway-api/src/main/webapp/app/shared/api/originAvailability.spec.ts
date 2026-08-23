/**
 * The Wave-28 SPA shipped before its backend half, so `/srv/goods/origin` 404s
 * on production today. The call is fail-open and the checkout is fine either
 * way — what this pins is that we stop ASKING once the endpoint has said it
 * does not exist, while a merely-ill endpoint stays retryable.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import { catalogApi, __resetOriginAvailability } from './catalogApi';

const mockGet = baseAxios.get as jest.Mock;
const httpError = (status: number) => Object.assign(new Error(`HTTP ${status}`), { response: { status } });

beforeEach(() => {
  __resetOriginAvailability();
  mockGet.mockReset();
});

it('returns the measured origins when the endpoint exists', async () => {
  mockGet.mockReturnValue(Promise.resolve({ data: { errno: 0, data: { list: [{ goodsId: 1, originCountry: 'DE' }] } } }));
  await expect(catalogApi.goodsOrigin([1])).resolves.toEqual({ list: [{ goodsId: 1, originCountry: 'DE' }] });
});

it('stops asking after a 404 — the backend half is not built yet', async () => {
  mockGet.mockReturnValue(Promise.reject(httpError(404)));
  await expect(catalogApi.goodsOrigin([1])).rejects.toBeTruthy();
  expect(mockGet).toHaveBeenCalledTimes(1);

  await expect(catalogApi.goodsOrigin([2])).resolves.toEqual({ list: [] });
  await expect(catalogApi.goodsOrigin([3])).resolves.toEqual({ list: [] });
  expect(mockGet).toHaveBeenCalledTimes(1); // no further requests
});

it('treats 501 the same way', async () => {
  mockGet.mockReturnValue(Promise.reject(httpError(501)));
  await expect(catalogApi.goodsOrigin([1])).rejects.toBeTruthy();
  await expect(catalogApi.goodsOrigin([2])).resolves.toEqual({ list: [] });
  expect(mockGet).toHaveBeenCalledTimes(1);
});

it('keeps retrying a 5xx — ill is not the same as absent', async () => {
  mockGet.mockReturnValue(Promise.reject(httpError(503)));
  await expect(catalogApi.goodsOrigin([1])).rejects.toBeTruthy();
  await expect(catalogApi.goodsOrigin([2])).rejects.toBeTruthy();
  expect(mockGet).toHaveBeenCalledTimes(2);
});

it('keeps retrying a network failure with no status at all', async () => {
  mockGet.mockReturnValue(Promise.reject(new Error('network down')));
  await expect(catalogApi.goodsOrigin([1])).rejects.toBeTruthy();
  await expect(catalogApi.goodsOrigin([2])).rejects.toBeTruthy();
  expect(mockGet).toHaveBeenCalledTimes(2);
});

it('picks the endpoint back up in a fresh session once the backend ships', async () => {
  mockGet.mockReturnValue(Promise.reject(httpError(404)));
  await expect(catalogApi.goodsOrigin([1])).rejects.toBeTruthy();
  await expect(catalogApi.goodsOrigin([2])).resolves.toEqual({ list: [] });

  __resetOriginAvailability(); // a new page load
  mockGet.mockReturnValue(Promise.resolve({ data: { errno: 0, data: { list: [{ goodsId: 2, originCountry: 'DE' }] } } }));
  await expect(catalogApi.goodsOrigin([2])).resolves.toEqual({ list: [{ goodsId: 2, originCountry: 'DE' }] });
});
