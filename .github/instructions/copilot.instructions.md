## Tool Selection Strategy & Constraints

1. **Prioritize CodeGraph / Precision Retrieval**:
   - ALWAYS search for definition, references, implementation, and context using CodeGraph / Symbol Navigation first.
   - DO NOT invoke broad file exploration (`explore`, repo-wide semantic search, or fuzzy file listing) unless CodeGraph explicitly returns empty results or missing symbols.

2. **Token & Latency Efficiency**:
   - Treat `explore` tools as high-cost fallback actions.
   - Limit the context window to current active editor files and explicitly tagged symbols (`#file`, `#symbol`).

3. **Execution Workflow**:
   - Step 1: Look up symbols/types via CodeGraph.
   - Step 2: Read only the target files returned by CodeGraph.
   - Step 3 (Only if needed): Run `explore` tools IF and ONLY IF precision graph lookup fails.