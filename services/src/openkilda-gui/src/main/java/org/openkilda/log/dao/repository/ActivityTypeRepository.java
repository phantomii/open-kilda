package org.openkilda.log.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.openkilda.log.dao.entity.ActivityTypeEntity;

@Repository
public interface ActivityTypeRepository extends JpaRepository<ActivityTypeEntity, Long> {

}
