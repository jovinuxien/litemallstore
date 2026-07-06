/**
 * Anti-corruption layer for the OCS (Open Commerce Search) REST APIs.
 *
 * <p><strong>Boundary rule:</strong> nothing in {@code domain.**} may import
 * from this package. Domain code talks to the
 * {@link org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer}
 * port; the implementation here is the only place that knows OCS exists. The
 * search and suggest endpoints are surfaced to controllers through
 * {@code application/search/SearchService} for the same reason.
 *
 * <p>If you find yourself wanting to import an OCS type from {@code domain.**},
 * the right answer is to grow the port interface or add a new application
 * service — not to bend the boundary.
 */
package org.linlinjava.litemall.goods.infrastructure.acl.ocs;
