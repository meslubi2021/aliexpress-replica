package com.msa.repos;


import com.msa.models.ServiceConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;


public interface ServiceConfigurationRepo extends MongoRepository<ServiceConfiguration, Long> {
    @Query("{ 'serviceName' : ?0 }")
    ServiceConfiguration findByServiceName(String serviceName);
}
