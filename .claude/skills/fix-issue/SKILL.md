---
name: fix-issue
description: Fix a GitHub issue
disable-model-invocation: true
---

Fix GitHub issue $ARGUMENTS following our coding standards.

If no issue number is provided in $ARGUMENTS, derive it from the current git branch name (e.g., branch `uchicom/2630` → issue #2630).

1. Determine the issue number: use $ARGUMENTS if provided, otherwise run `git branch --show-current` and extract the trailing number
2. Read the issue description with `gh issue view <number>`
3. Understand the requirements from the issue title and body
4. 提案や確認がある場合は、事前に質問する
5. Implement the fix following the project's coding standards
6. Write tests if applicable
7. 実装・テストを自己レビューして修正する
8. コミットはしない、人が実施する
