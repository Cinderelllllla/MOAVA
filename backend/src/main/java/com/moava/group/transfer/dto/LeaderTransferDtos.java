package com.moava.group.transfer.dto;

import java.util.List;

public final class LeaderTransferDtos {

    private LeaderTransferDtos() {}

    public record CreateRequest(
            Long toMemberId
    ) {}

    public record DecisionRequest(
            Boolean accepted
    ) {}

    public record ConsentRequest(
            Boolean agreed
    ) {}

    public record MemberOption(
            Long memberId,
            String name
    ) {}

    public record StatusResponse(
            Long groupId,
            Long transferId,
            Long fromMemberId,
            Long toMemberId,
            String status,
            boolean targetAccepted,
            boolean isTarget,
            boolean isOwner,
            String myConsent,
            boolean canConsent,
            int totalMembers,
            int agreedMembers,
            int rejectedMembers,
            int pendingMembers,
            boolean canConfirm,
            List<MemberOption> candidates
    ) {}
}
