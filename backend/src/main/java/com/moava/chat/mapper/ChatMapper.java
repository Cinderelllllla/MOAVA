package com.moava.chat.mapper;

import com.moava.chat.dto.ChatAccess;
import com.moava.chat.dto.ChatMessageRow;
import com.moava.chat.dto.ChatParticipant;
import com.moava.chat.dto.ChatRoomRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F34 채팅 전용 쿼리. 모임·회원·제재 테이블은 다른 팀원 Mapper 를 고치지 않고 여기서 읽기만 한다.
 * 채팅 본문(BODY_ENC)은 암호문으로만 오간다.
 */
@Mapper
public interface ChatMapper {

    /** 이 회원이 이 모임 채팅방에서 무엇을 할 수 있는지 판단 재료. 모임이 없으면 null. */
    ChatAccess selectAccess(@Param("groupId") long groupId, @Param("memberId") long memberId);

    /** 내가 승인된(ACTIVE) 회원인 모임 목록 (해산 완료 포함 — 읽기 전용). */
    List<ChatRoomRow> selectRooms(@Param("memberId") long memberId);

    /** beforeId 보다 오래된 메시지를 최신부터 limit 개 (beforeId 가 null 이면 가장 최신부터). */
    List<ChatMessageRow> selectMessages(@Param("groupId") long groupId, @Param("beforeId") Long beforeId,
                                        @Param("limit") int limit);

    long nextMessageId();

    int insertMessage(@Param("messageId") long messageId, @Param("groupId") long groupId,
                      @Param("memberId") long memberId, @Param("bodyEnc") String bodyEnc,
                      @Param("keyVer") int keyVer, @Param("sentAt") LocalDateTime sentAt);

    /** 시스템 메시지 저장 (MSG_TYPE='SYSTEM', MEMBER_ID NULL). 운영 규칙 변경 안내 등 서버가 쓰는 메시지 전용. */
    int insertSystemMessage(@Param("messageId") long messageId, @Param("groupId") long groupId,
                            @Param("bodyEnc") String bodyEnc, @Param("keyVer") int keyVer,
                            @Param("sentAt") LocalDateTime sentAt);

    String selectMemberName(@Param("memberId") long memberId);

    /** 채팅방 참여자(모임의 ACTIVE 회원). 모임장 먼저, 그다음 가입 순. */
    List<ChatParticipant> selectParticipants(@Param("groupId") long groupId);
}
