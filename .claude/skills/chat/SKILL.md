---
name: chat
description: 파일을 수정하지 않고 질문에만 답변하는 채팅 모드. 코드 분석, 개념 설명, 아키텍처 논의 등 순수 대화가 필요할 때 사용.
disable-model-invocation: true
allowed-tools: Read, Grep, Glob
---

채팅 모드가 활성화되었습니다.

이 모드에서는 파일 수정, 생성, 삭제, 명령어 실행을 하지 않습니다.
코드 읽기와 검색만 허용되며, 질문에 대한 답변과 분석에 집중합니다.

$ARGUMENTS