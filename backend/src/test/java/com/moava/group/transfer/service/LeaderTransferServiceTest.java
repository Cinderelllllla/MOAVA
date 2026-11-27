package com.moava.group.transfer.service;

import com.moava.group.transfer.dto.LeaderTransferDtos.*;
import com.moava.group.transfer.mapper.LeaderTransferMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaderTransferServiceTest {

    private LeaderTransferMapper mapper;
    private LeaderTransferService service;

    @BeforeEach
    void setup() {

        mapper = mock(LeaderTransferMapper.class);

        service =
                new LeaderTransferService(mapper);

        when(mapper.lockGroup(7L))
                .thenReturn(
                        Map.of(
                                "GROUP_ID", 7L,
                                "OWNER_ID", 11L,
                                "STATUS", "ACTIVE"
                        )
                );

        when(
                mapper.countActive(
                        eq(7L),
                        anyLong()
                )
        ).thenReturn(1);

        when(mapper.candidates(7L, 11L))
                .thenReturn(
                        List.of(
                                new MemberOption(
                                        12L,
                                        "회원"
                                )
                        )
                );
    }

    @Test
    void onlyOwnerCanRequest() {

        assertThrows(
                ResponseStatusException.class,
                () -> service.request(
                        7L,
                        12L,
                        new CreateRequest(13L)
                )
        );

        verify(
                mapper,
                never()
        ).create(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong()
        );
    }

    @Test
    void newRequestRequiresNoPending() {

        when(mapper.findPending(7L))
                .thenReturn(
                        Map.of(
                                "TRANSFER_ID", 5L
                        )
                );

        assertThrows(
                ResponseStatusException.class,
                () -> service.request(
                        7L,
                        11L,
                        new CreateRequest(12L)
                )
        );

        verify(
                mapper,
                never()
        ).create(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong()
        );
    }

    @Test
    void noConfirmationBeforeConsent() {

        when(mapper.findPending(7L))
                .thenReturn(
                        Map.of(
                                "TRANSFER_ID", 9L,
                                "FROM_MEMBER_ID", 11L,
                                "TO_MEMBER_ID", 12L,
                                "ACCEPTED_YN", "Y"
                        )
                );

        when(mapper.countActiveAll(7L))
                .thenReturn(2);

        when(
                mapper.countAgreed(7L, 9L)
        ).thenReturn(1);

        assertThrows(
                ResponseStatusException.class,
                () -> service.confirm(
                        7L,
                        9L,
                        11L
                )
        );

        verify(
                mapper,
                never()
        ).changeOwner(
                anyLong(),
                anyLong(),
                anyLong()
        );
    }

    @Test
    void rejectedConsentClosesPending() {

        Map<String, Object> request =
                Map.of(
                        "TRANSFER_ID", 9L,
                        "FROM_MEMBER_ID", 11L,
                        "TO_MEMBER_ID", 12L,
                        "ACCEPTED_YN", "Y"
                );

        when(mapper.findPending(7L))
                .thenReturn(request)
                .thenReturn(null);

        when(
                mapper.recordConsent(
                        9L,
                        7L,
                        12L,
                        "N"
                )
        ).thenReturn(1);

        when(mapper.cancel(9L, 7L))
                .thenReturn(1);

        when(
                mapper.audit(
                        anyLong(),
                        anyLong(),
                        any(),
                        anyString(),
                        anyString()
                )
        ).thenReturn(1);

        StatusResponse result =
                service.consent(
                        7L,
                        9L,
                        12L,
                        new ConsentRequest(false)
                );

        assertEquals(
                "NONE",
                result.status()
        );

        verify(mapper)
                .cancel(9L, 7L);
    }
}
