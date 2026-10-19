package edu.vit.hcms.core.interfaces;

import java.util.List;

/**
 * Generic search interface for entities that support text-based querying.
 * Demonstrates generics with a functional search contract.
 *
 * @param <T> the entity type returned by search
 */
public interface Searchable<T> {

    /**
     * Searches entities matching the given query string.
     *
     * @param query the search query (free text)
     * @return list of matching entities
     */
    List<T> search(String query);

    /**
     * Searches with pagination support.
     *
     * @param query  the search query
     * @param offset starting position
     * @param limit  maximum results to return
     * @return paginated list of matching entities
     */
    List<T> search(String query, int offset, int limit);
}
