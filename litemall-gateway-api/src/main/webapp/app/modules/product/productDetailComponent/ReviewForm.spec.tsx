import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';

/**
 * Reviews are purchase-gated (goods-management F17). The form used to answer EVERY
 * failure with "could not be submitted, please try again" — including the two refusals
 * no retry can change. Driven through the real userApi + unwrap, so the errno
 * localisation and the request body are what production sends.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));
jest.mock('app/components/commonComponents/ImageUploader', () => ({ __esModule: true, default: () => null }));

import { baseAxios } from 'app/config/axiosinstance';

import ReviewForm from './ReviewForm';

const mockPost = baseAxios.post as jest.Mock;
const envelope = (errno: number, errmsg = '', data: unknown = null) => Promise.resolve({ data: { errno, errmsg, data } });

const submit = async (text = 'Sturdy and well made') => {
  fireEvent.change(screen.getByPlaceholderText(/Share your experience/), { target: { value: text } });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Submit review' }));
  });
};
const formShown = () => screen.queryByPlaceholderText(/Share your experience/) != null;

beforeEach(() => mockPost.mockReset());

describe('ReviewForm', () => {
  it('posts the review and thanks the customer', async () => {
    mockPost.mockReturnValue(envelope(0));
    const onSubmitted = jest.fn();
    const onSettled = jest.fn();
    render(<ReviewForm goodsId={1055022} onSubmitted={onSubmitted} onSettled={onSettled} />);
    await submit();
    const [url, body] = mockPost.mock.calls[0];
    expect(url).toMatch(/\/srv\/comment\/post$/);
    expect(body).toMatchObject({ type: 0, valueId: 1055022, star: 5, content: 'Sturdy and well made' });
    // No order ⇒ no orderId key at all: the server then picks the oldest unreviewed purchase.
    expect('orderId' in body).toBe(false);
    expect(screen.getByText(/Thanks! Your review has been submitted/)).toBeTruthy();
    expect(onSubmitted).toHaveBeenCalledTimes(1);
    expect(onSettled).toHaveBeenCalledWith('submitted', expect.stringMatching(/Thanks/));
  });

  it('pins the purchase check to the order it is written from', async () => {
    mockPost.mockReturnValue(envelope(0));
    render(<ReviewForm goodsId={1055022} orderId={68} />);
    await submit();
    expect(mockPost.mock.calls[0][1]).toMatchObject({ valueId: 1055022, orderId: 68 });
  });

  it('671 already reviewed ⇒ the form closes on that fact, not on "try again"', async () => {
    mockPost.mockReturnValue(envelope(671, 'You have already reviewed this purchase'));
    const onSettled = jest.fn();
    render(<ReviewForm goodsId={1055022} orderId={68} onSettled={onSettled} />);
    await submit();
    expect(screen.getByText('You have already reviewed this purchase')).toBeTruthy();
    expect(screen.queryByText(/could not be submitted/)).toBeNull();
    expect(formShown()).toBe(false);
    expect(onSettled).toHaveBeenCalledWith('already-reviewed', 'You have already reviewed this purchase');
  });

  it('670 not a buyer ⇒ the reason is stated and no retry is offered', async () => {
    mockPost.mockReturnValue(envelope(670, 'Only customers who received this product can review it'));
    const onSubmitted = jest.fn();
    const onSettled = jest.fn();
    render(<ReviewForm goodsId={1055022} onSubmitted={onSubmitted} onSettled={onSettled} />);
    await submit();
    expect(screen.getByText('Only customers who received this product can review it')).toBeTruthy();
    expect(formShown()).toBe(false);
    expect(onSubmitted).not.toHaveBeenCalled();
    expect(onSettled).toHaveBeenCalledWith('not-eligible', expect.any(String));
  });

  it('the same refusal on a non-2xx response is read the same way', async () => {
    mockPost.mockReturnValue(Promise.reject({ response: { status: 400, data: { errno: 671, errmsg: 'You have already reviewed this purchase' } } }));
    render(<ReviewForm goodsId={1} />);
    await submit();
    expect(screen.getByText('You have already reviewed this purchase')).toBeTruthy();
    expect(formShown()).toBe(false);
  });

  it('any other server refusal is shown in the server words and stays retryable', async () => {
    mockPost.mockReturnValueOnce(envelope(402, 'Invalid parameter value')).mockReturnValueOnce(envelope(0));
    render(<ReviewForm goodsId={1} />);
    await submit();
    expect(screen.getByText('Invalid parameter value')).toBeTruthy();
    expect(formShown()).toBe(true);
    await submit();
    expect(screen.getByText(/Thanks!/)).toBeTruthy();
  });

  it('a dropped connection gets the generic retry line', async () => {
    mockPost.mockReturnValue(Promise.reject(new Error('Network Error')));
    render(<ReviewForm goodsId={1} />);
    await submit();
    expect(screen.getByText(/could not be submitted/)).toBeTruthy();
    expect(formShown()).toBe(true);
  });
});
