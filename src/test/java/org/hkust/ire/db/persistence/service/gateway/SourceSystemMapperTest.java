package org.hkust.ire.db.persistence.service.gateway;

import org.hkust.ire.common.constant.SourceSystemConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SourceSystemMapperTest {

    private final SourceSystemMapper sourceSystemMapper = new SourceSystemMapper();

    @Test
    public void testDetectSourceSystemWithNullSourceAndNullPayloadFallsBackToThirdParty() {
        String detected = sourceSystemMapper.detectSourceSystem(null, null);
        assertEquals(SourceSystemConstant.THIRD_PARTY, detected);
    }
}

