package com.moava.group.transfer.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moava.group.transfer.dto.LeaderTransferDtos.MemberOption;

@Mapper
public interface LeaderTransferMapper {

    Map<String, Object> lockGroup(
            @Param("groupId") Long groupId
    );

    Map<String, Object> findPending(
            @Param("groupId") Long groupId
    );

    int countActive(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    int countActiveAll(
            @Param("groupId") Long groupId
    );

    int countAgreed(
            @Param("groupId") Long groupId,
            @Param("transferId") Long transferId
    );

    int countRejected(
            @Param("groupId") Long groupId,
            @Param("transferId") Long transferId
    );

    String myConsent(
            @Param("groupId") Long groupId,
            @Param("transferId") Long transferId,
            @Param("memberId") Long memberId
    );

    List<MemberOption> candidates(
            @Param("groupId") Long groupId,
            @Param("ownerId") Long ownerId
    );

    Long nextId();

    int create(
            @Param("transferId") Long transferId,
            @Param("groupId") Long groupId,
            @Param("fromId") Long fromId,
            @Param("toId") Long toId
    );

    int accept(
            @Param("transferId") Long transferId,
            @Param("groupId") Long groupId
    );

    int cancel(
            @Param("transferId") Long transferId,
            @Param("groupId") Long groupId
    );

    int recordConsent(
            @Param("transferId") Long transferId,
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("agreedYn") String agreedYn
    );

    int changeOwner(
            @Param("groupId") Long groupId,
            @Param("oldId") Long oldId,
            @Param("newId") Long newId
    );

    int requireCurrentAccountReverification(
            @Param("groupId") Long groupId
    );

    int confirm(
            @Param("transferId") Long transferId,
            @Param("groupId") Long groupId
    );

    int audit(
            @Param("groupId") Long groupId,
            @Param("actorId") Long actorId,
            @Param("targetId") Long targetId,
            @Param("eventType") String eventType,
            @Param("description") String description
    );
}
