package com.university.ire.service.identity;

import com.university.ire.entity.Identity;
import com.university.ire.entity.IdentityGraph;
import com.university.ire.entity.RelationshipType;
import com.university.ire.repository.IdentityGraphRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IdentityGraphService {

    private final IdentityGraphRepository identityGraphRepository;

    public IdentityGraphService(IdentityGraphRepository identityGraphRepository) {
        this.identityGraphRepository = identityGraphRepository;
    }

    public IdentityGraph link(Identity from, Identity to, RelationshipType type, double strength) {
        IdentityGraph graph = new IdentityGraph();
        graph.setFromIdentity(from);
        graph.setToIdentity(to);
        graph.setRelationshipType(type);
        graph.setStrength(strength);
        return identityGraphRepository.save(graph);
    }

    public List<IdentityGraph> related(Long identityId) {
        return identityGraphRepository.findByFromIdentityIdOrToIdentityId(identityId, identityId);
    }
}
