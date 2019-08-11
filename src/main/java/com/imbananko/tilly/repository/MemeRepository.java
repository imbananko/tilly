package com.imbananko.tilly.repository;

import com.imbananko.tilly.model.MemeEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemeRepository extends CrudRepository<MemeEntity, String> {}
