import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * Writing a review from the order page: the request names the order (so the purchase
 * check cannot fall through to another purchase), and a line whose review is over for
 * good stops offering "Write a review".
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));
jest.mock('app/components/commonComponents/ImageUploader', () => ({ __esModule: true, default: () => null }));
jest.mock('./AftersalePanel', () => ({ __esModule: true, default: () => null }));
jest.mock('./DisputePanel', () => ({ __esModule: true, default: () => null }));
jest.mock('./OrderTimelinePanel', () => ({ __esModule: true, default: () => null }));
jest.mock('./TrackingPanel', () => ({ __esModule: true, default: () => null }));

import { baseAxios } from 'app/config/axiosinstance';

import OrderDetail from './OrderDetail';

const mockGet = baseAxios.get as jest.Mock;
const mockPost = baseAxios.post as jest.Mock;

const order = {
  id: 68,
  orderSn: 'SN68',
  orderStatusText: 'Delivered',
  actualPrice: 30,
  handleOption: { comment: true },
  orderGoods: [
    { id: 64, goodsId: 1055022, goodsName: 'Garden lamp, warm white', number: 1, price: 10 },
    { id: 65, goodsId: 1055022, goodsName: 'Garden lamp, cool white', number: 1, price: 10 },
  ],
};

const mount = async () => {
  render(
    <MemoryRouter initialEntries={['/order/68']}>
      <Routes>
        <Route path='/order/:id' element={<OrderDetail />} />
      </Routes>
    </MemoryRouter>
  );
  await act(async () => {
    for (let i = 0; i < 6; i += 1) await Promise.resolve();
  });
};

const writeButtons = () => screen.queryAllByRole('button', { name: /Write a review/ });
const submitReview = async () => {
  fireEvent.change(screen.getByPlaceholderText(/Share your experience/), { target: { value: 'Works well' } });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Submit review' }));
  });
};

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
  mockGet.mockImplementation(() => Promise.resolve({ data: { errno: 0, data: order } }));
});

describe('OrderDetail reviews', () => {
  it('offers a review per LINE and sends the order id with it', async () => {
    mockPost.mockReturnValue(Promise.resolve({ data: { errno: 0, data: null } }));
    await mount();
    expect(writeButtons()).toHaveLength(2); // two lines of one product = two purchases
    fireEvent.click(writeButtons()[0]);
    expect(writeButtons()).toHaveLength(1); // only the opened line shows a form
    await submitReview();
    expect(mockPost.mock.calls[0][1]).toMatchObject({ valueId: 1055022, orderId: 68 });
  });

  it('a reviewed line stops offering the button, also after another line is opened', async () => {
    mockPost.mockReturnValue(Promise.resolve({ data: { errno: 0, data: null } }));
    await mount();
    fireEvent.click(writeButtons()[0]);
    await submitReview();
    fireEvent.click(writeButtons()[0]); // the one remaining button belongs to the second line
    expect(writeButtons()).toHaveLength(0);
    expect(document.querySelectorAll('[data-review-settled]')).toHaveLength(1);
    expect(screen.getByText(/Thanks! Your review has been submitted/)).toBeTruthy();
  });

  it('"already reviewed" closes that line with the server words', async () => {
    mockPost.mockReturnValue(Promise.resolve({ data: { errno: 671, errmsg: 'You have already reviewed this purchase' } }));
    await mount();
    fireEvent.click(writeButtons()[0]);
    await submitReview();
    expect(screen.getByText('You have already reviewed this purchase')).toBeTruthy();
    fireEvent.click(writeButtons()[0]);
    expect(document.querySelector('[data-review-settled]')?.textContent).toContain('You have already reviewed this purchase');
  });
});
