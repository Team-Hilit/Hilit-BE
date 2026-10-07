# shared-docs 업데이트 스킬

shared-docs 서브모듈을 원격 최신 상태로 업데이트한다.

## 실행 순서

1. 현재 shared-docs 커밋 해시를 확인한다:
   ```
   git submodule status shared-docs
   ```
2. 서브모듈을 원격 최신 커밋으로 업데이트한다:
   ```
   git submodule update --remote shared-docs
   ```
3. 업데이트 결과를 확인한다:
   ```
   git submodule status shared-docs
   ```
4. 변경이 있으면 사용자에게 변경된 커밋 해시를 보여주고, 메인 레포에 반영할지 물어본다.
5. 사용자가 동의하면 스테이징하고 커밋한다:
   ```
   git add shared-docs
   git commit -m "chore: update shared-docs submodule"
   ```

## 규칙

- 업데이트 전후 커밋 해시를 반드시 사용자에게 보여준다.
- 이미 최신 상태면 "이미 최신 상태입니다"라고 알리고 종료한다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>` 을 추가한다.

## 실행

위 순서대로 바로 실행한다.
