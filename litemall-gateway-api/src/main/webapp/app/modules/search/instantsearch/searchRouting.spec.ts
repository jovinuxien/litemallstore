import { searchRouting } from './searchRouting';
import { PRIMARY_INDEX, sortIndex } from './litemallSearchClient';

/**
 * Wave-19: the "Has coupon" toggle (coupon_flag=1) must round-trip the URL —
 * `/search?coupon_flag=1` is the /coupons center's shareable preset link, and
 * a toggled facet must survive refresh/back exactly like every other filter.
 */
describe('searchRouting coupon_flag toggle mapping', () => {
  const { stateToRoute, routeToState } = searchRouting.stateMapping;

  it('serialises the toggled coupon_flag facet as coupon_flag=1', () => {
    const route = stateToRoute({
      [PRIMARY_INDEX]: {
        query: 'lamp',
        toggle: { coupon_flag: true },
      },
    } as any);
    expect(route.q).toBe('lamp');
    expect(route.coupon_flag).toBe('1');
  });

  it('omits coupon_flag from the route when the toggle is off', () => {
    const route = stateToRoute({
      [PRIMARY_INDEX]: {
        query: 'lamp',
        toggle: { coupon_flag: false },
      },
    } as any);
    expect(route.coupon_flag).toBeUndefined();
  });

  it('parses ?coupon_flag=1 into the toggle uiState slice (not refinementList)', () => {
    const state = routeToState({ q: 'lamp', coupon_flag: '1' })[PRIMARY_INDEX] as any;
    expect(state.toggle).toEqual({ coupon_flag: true });
    // Must NOT land in refinementList — no mounted widget consumes it there,
    // so InstantSearch would silently drop the filter.
    expect(state.refinementList).toBeUndefined();
  });

  it('ignores a non-"1" coupon_flag value', () => {
    const state = routeToState({ coupon_flag: '0' })[PRIMARY_INDEX] as any;
    expect(state.toggle).toBeUndefined();
    expect(state.refinementList).toBeUndefined();
  });

  it('round-trips toggle + refinement + range + sort together', () => {
    const uiState = {
      [PRIMARY_INDEX]: {
        query: 'desk',
        toggle: { coupon_flag: true },
        refinementList: { brand: ['Acme'], category_ids: ['1005000'] },
        range: { price: '10:50' },
        sortBy: sortIndex('price'),
        page: 3,
      },
    } as any;
    const roundTripped = routeToState(stateToRoute(uiState))[PRIMARY_INDEX] as any;
    expect(roundTripped.query).toBe('desk');
    expect(roundTripped.toggle).toEqual({ coupon_flag: true });
    expect(roundTripped.refinementList).toEqual({ brand: ['Acme'], category_ids: ['1005000'] });
    expect(roundTripped.range).toEqual({ price: '10:50' });
    expect(roundTripped.sortBy).toBe(sortIndex('price'));
    expect(roundTripped.page).toBe(3);
  });
});
