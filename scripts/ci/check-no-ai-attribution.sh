#!/usr/bin/env bash
# PR의 커밋 메시지·제목·본문에 AI 도구 서명(예: "Co-Authored-By: Claude", "Generated with Claude Code")이
# 들어 있으면 실패한다. 팀 규칙(AGENTS.md "AI 서명 금지")을 도구와 상관없이 CI에서 강제한다.
#
# 사용법: 검사할 텍스트를 표준 입력으로 넘긴다.
#   git log --format=%B origin/develop..HEAD | bash scripts/ci/check-no-ai-attribution.sh
# CI(.github/workflows/no-ai-attribution.yml)는 PR 커밋 메시지와 PR 제목·본문을 합쳐서 넘긴다.
set -euo pipefail

# 대소문자 무시. 서명은 항상 줄 맨 앞에 붙으므로 줄 시작만 본다.
# 문장 중간에서 서명 예시를 설명하는 경우(이 규칙을 설명하는 커밋·PR 등)는 걸리지 않는다.
PATTERN='^[[:space:]]*(co-authored-by:.*(claude|anthropic|codex|openai|chatgpt|copilot)|(🤖[[:space:]]*)?generated (with|by) \[?(claude|codex|chatgpt|copilot))'

matches=$(grep -inE "$PATTERN" || true)
if [ -n "$matches" ]; then
  echo "✗ AI 도구 서명이 들어 있습니다. 커밋 메시지·PR 본문에서 지워주세요 (AGENTS.md \"AI 서명 금지\")."
  echo "$matches" | sed 's/^/  /'
  echo
  echo "커밋 메시지를 고치는 방법: git rebase -i 로 해당 커밋을 reword 하거나, 마지막 커밋이면 git commit --amend"
  exit 1
fi
echo "✓ AI 도구 서명 없음"
