/* API 진입점 — 화면은 이 파일만 import 합니다.
   백엔드 연동 시: ./mock 구현을 fetch 기반으로 바꿔도 화면 코드는 그대로입니다. */
export * as auth from './auth';
export * as finance from './finance';
export * as groups from './groups';
export * as procedures from './procedures';
export * as proofs from './proofs';
export * as admin from './admin';
export { db, subscribe, getVersion, me, isAdmin, currentUser, memberName, joined, leader, activeJoined, isEnded, groupVisible, proofPeriod, currentPeriod, paidAny, paidThisPeriod, ensureJoinCode } from './mock/db';
