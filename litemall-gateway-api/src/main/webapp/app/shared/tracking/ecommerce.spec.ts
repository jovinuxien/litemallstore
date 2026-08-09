import { trackAddToCart, trackBeginCheckout, trackProductView, trackPurchase } from './ecommerce';
import { pixelTrack } from './metaPixel';

jest.mock('./metaPixel', () => ({ pixelTrack: jest.fn() }));
jest.mock('./matomo', () => ({ trackCartAdd: jest.fn(), trackEvent: jest.fn(), trackOrder: jest.fn() }));
jest.mock('./firstParty', () => ({ fpTrack: jest.fn(), numericGoodsId: jest.fn(() => 1) }));

const pixelCalls = pixelTrack as jest.Mock;

/** Wave 24: the store prices and charges EUR — every pixel event must say so. */
describe('ecommerce events carry EUR', () => {
  beforeEach(() => {
    pixelCalls.mockClear();
    sessionStorage.clear();
  });

  it('ViewContent / AddToCart / InitiateCheckout / Purchase all send currency EUR', () => {
    trackProductView({ id: '1', name: 'Lamp', price: 12.5 });
    trackAddToCart({ id: '1', name: 'Lamp', price: 12.5, quantity: 2 }, 25);
    trackBeginCheckout(25, 2);
    trackPurchase({ orderId: 7, revenue: 25 });

    expect(pixelCalls).toHaveBeenCalledTimes(4);
    for (const [, payload] of pixelCalls.mock.calls) {
      expect(payload.currency).toBe('EUR');
    }
  });
});
