package com.university.ire.service.gateway;

import com.university.ire.dto.ApiGatewayRequest;
import com.university.ire.dto.CanonicalIdentity;
import org.springframework.stereotype.Service;

@Service
public class DynamicPayloadParser {

    private final SourceSystemMapper sourceSystemMapper;

    public DynamicPayloadParser(SourceSystemMapper sourceSystemMapper) {
        this.sourceSystemMapper = sourceSystemMapper;
    }

    public CanonicalIdentity parse(ApiGatewayRequest request) {
        return sourceSystemMapper.toCanonical(request.sourceSystem(), request.sourceRecordId(), request.payload());
    }
}
