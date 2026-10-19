package edu.vit.hcms.core.interfaces;

import java.util.List;
import java.util.Optional;

/**
 * Generic repository interface demonstrating the DAO pattern with generics.
 * All persistence modules implement this for their domain entities.
 *
 * @param <T>  the entity type
 * @param <ID> the primary key type
 */
public interface Repository<T, ID> {

    /**
     * Saves a new entity to the data store.
     *
     * @param entity the entity to save
     * @return the saved entity (with generated ID if applicable)
     */
    T save(T entity);

    /**
     * Finds an entity by its primary key.
     *
     * @param id the primary key
     * @return an Optional containing the entity if found
     */
    Optional<T> findById(ID id);

    /**
     * Returns all entities of this type.
     *
     * @return list of all entities
     */
    List<T> findAll();

    /**
     * Updates an existing entity.
     *
     * @param entity the entity with updated fields
     * @return the updated entity
     */
    T update(T entity);

    /**
     * Deletes an entity by its primary key.
     *
     * @param id the primary key
     * @return true if the entity was deleted
     */
    boolean deleteById(ID id);

    /**
     * Counts total entities.
     *
     * @return the count
     */
    long count();
}
