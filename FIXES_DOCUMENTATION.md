# Fixes for Score Calculation and Topic Mapping Issues

## Issues Addressed

### 1. Problem Scores Exceeding 100%

**Issue**: Tasks/problems were showing scores greater than 100% in the UI.

**Root Cause**: In the `ProblemRetrievalService.hybridSearch()` method, when a problem was found by both exact topic matching and semantic search, the scores were combined without clamping:
```kotlin
score = existingResult.score + (semanticScore * 0.5)  // Could exceed 1.0
```

**Fix**: Added score clamping after combining scores to ensure they never exceed 100%:
```kotlin
score = clampScore(existingResult.score + (semanticScore * 0.5))  // Now clamped to [0.0, 1.0]
```

**Location**: `src/main/kotlin/com/Anton15K/LocalAIDemo/service/ProblemRetrievalService.kt`, line 168

---

### 2. Incorrect Topic Mapping for Pure Geometry Lectures

**Issue**: When lectures discussed vectors in a pure geometry context (e.g., Euclidean geometry, coordinate geometry), the system sometimes mapped them to "Linear Algebra" instead of "Geometry".

**Root Causes**:
1. The LLM prompt in `ThemeExtractionService` had brief disambiguation instructions that were easily overlooked
2. The `TopicMappingService` didn't have strong enough geometric context detection
3. Missing common geometric terms in the hint detection system

**Fixes Applied**:

#### a) Enhanced and Simplified LLM Prompt (`ThemeExtractionService.kt`)
- **Initial Fix (commit 859ecd9)**: Added comprehensive "CRITICAL DISAMBIGUATION RULES" with detailed criteria
- **Simplified Fix (commit 08b7af5)**: Condensed the rules into concise bullet points after discovering that complex structured prompts with special characters (✓) were causing LLM parsing failures
- Current prompt provides clear disambiguation rules: prefer GEOMETRY for vectors as arrows/spatial relationships, choose LINEAR ALGEBRA only for abstract vector spaces/eigenvalues
- Maintains "when in doubt with vectors, prefer GEOMETRY" default behavior

**Location**: `src/main/kotlin/com/Anton15K/LocalAIDemo/service/ThemeExtractionService.kt`, lines 85-95

#### b) Strengthened Geometric Scoring (`TopicMappingService.kt`)
- Increased geometry boost from +6 to +10 when geometric hints are present and topic contains "geometry"
- Increased coordinate-related boost from +2 to +4 for geometry contexts
- This makes geometry mapping much more likely when geometric context is detected

**Location**: `src/main/kotlin/com/Anton15K/LocalAIDemo/service/TopicMappingService.kt`, lines 75, 79

#### c) Expanded Geometric Hint Detection (`TopicMappingService.kt`)
Added 15 more geometric keywords to `GEOMETRY_HINTS`:
- Basic geometric elements: point, points, segment, segments, ray, vertex, vertices
- Geometric relationships: congruent, similar, pythagorean, euclidean, cartesian
- Geometric objects: tangent, secant, arc, sector, geometric, shape, shapes

**Location**: `src/main/kotlin/com/Anton15K/LocalAIDemo/service/TopicMappingService.kt`, lines 165-167

---

### 3. Theme Extraction Failures ("No Changes" When Inserting Lectures)

**Issue**: When users inserted lectures, no themes were being extracted, resulting in "no changes" and no problem recommendations.

**Root Cause**: The enhanced disambiguation prompt (commit 859ecd9) contained:
- Special Unicode characters (✓ checkmarks) that confused some LLM models
- Complex nested structure with numbered sections and sub-bullets
- Longer prompt length that could exceed context limits for some models

When the LLM failed to generate valid JSON (due to prompt confusion), the `parseThemeResponse()` method would catch the exception and return an empty list, causing no themes to be extracted.

**Fix (commit 08b7af5)**: Simplified the LLM prompt while maintaining disambiguation logic:
- Removed special Unicode characters
- Condensed multi-level structure into simple bullet points
- Reduced prompt length by ~70% while keeping key disambiguation rules
- Maintained "prefer GEOMETRY over LINEAR ALGEBRA when in doubt" default behavior

**Before** (commit 859ecd9): Complex structured prompt
```
CRITICAL DISAMBIGUATION RULES:
1. GEOMETRY vs LINEAR ALGEBRA disambiguation:
   When you encounter vectors/matrices, carefully assess the context:
   ✓ Choose GEOMETRY when the lecture discusses:
   - Vectors as arrows or directed segments in 2D/3D space
   - Geometric properties: distances, angles, projections, perpendicularity
   - Coordinate geometry, points, lines, planes
   [... 25 more lines of detailed criteria ...]
```

**After** (commit 08b7af5): Concise and clear
```
Important disambiguation rule for vectors and matrices:
- Choose GEOMETRY if the lecture discusses vectors as arrows/directed segments in 2D/3D space,
  geometric properties (distances, angles, projections), coordinate geometry, or spatial relationships.
- Choose LINEAR ALGEBRA only if the lecture discusses abstract vector spaces, linear transformations
  as functions, eigenvalues/eigenvectors, basis/span, or theoretical algebraic properties.
- When in doubt with vectors, prefer GEOMETRY over LINEAR ALGEBRA.
```

**Location**: `src/main/kotlin/com/Anton15K/LocalAIDemo/service/ThemeExtractionService.kt`, lines 88-92

**Impact**: Lectures now successfully extract themes, leading to proper problem recommendations.

---

### 4. Enhanced Error Handling and Diagnostics (commit e4d8db3)

**Issue**: When theme extraction failed, errors were silently swallowed, making it difficult to diagnose issues. Users would see "no topics found" without understanding why.

**Root Cause**: 
- LLM connection failures returned empty lists without throwing exceptions
- JSON parsing errors were caught and returned empty lists
- No detailed logging of the extraction process

**Fixes Applied**:

#### a) Explicit Error Handling (`ThemeExtractionService.kt`)
Now throws descriptive RuntimeExceptions instead of returning empty lists:
- **Connection errors**: "Theme extraction failed: Unable to connect to LLM service. Please check that Ollama is running..."
- **Empty responses**: "Theme extraction failed: LLM returned empty response. Please check Ollama service..."
- **Parsing errors**: "Theme extraction failed: Unable to parse LLM response as JSON. The model may not be following the expected format..."

#### b) Enhanced Logging
Added DEBUG-level logging at key points:
- Prompt generation with character count
- LLM response receipt with length
- JSON parsing attempts with preview of content
- Success messages with theme count

#### c) Comprehensive Troubleshooting Guide (`TROUBLESHOOTING.md`)
Created detailed guide covering:
- Common issues (Ollama not running, model not available, format issues, etc.)
- Diagnostic steps with specific commands
- Model-specific recommendations
- Quick fix checklist
- Performance tips

**Location**: `src/main/kotlin/com/Anton15K/LocalAIDemo/service/ThemeExtractionService.kt`, lines 44-93, 143-157

**Impact**: 
- Users now see specific error messages on the lecture detail page
- Developers can diagnose issues quickly using logs
- Clear path to resolution for common problems
- Error messages are stored in the lecture's `errorMessage` field and displayed in the UI

---

## Configuration Recommendations

### Temperature Setting
The application now includes guidance on temperature settings in `application.properties`:

```properties
# Recommended range: 0.3-0.7 for better topic classification accuracy.
spring.ai.ollama.chat.options.temperature=0.5
```

**Explanation**:
- **Lower temperature (0.3-0.5)**: More deterministic, better for classification tasks, reduces "topic drift"
  - Recommended when you notice topics being misclassified frequently
  - Better for lectures with clear mathematical structure
  
- **Medium temperature (0.5-0.7)**: Balanced approach, good default
  - Recommended for most use cases
  - Current default: 0.5
  
- **Higher temperature (0.7-1.0)**: More creative but less consistent
  - NOT recommended for topic classification
  - May cause more Geometry → Linear Algebra drift

### Model Selection
Better alternatives to `mistral` for math topic classification:
1. `qwen2.5-math:7b` - Specialized for mathematical reasoning
2. `qwen2.5:14b` - Strong general model with good math understanding
3. Current: `qwen3-vl:235b-cloud` (if available in your Ollama registry)

To change model:
```properties
spring.ai.ollama.chat.options.model=qwen2.5-math:7b
```

Then pull the model:
```bash
ollama pull qwen2.5-math:7b
```

---

## Testing Your Changes

### Manual Testing for Score Clamping

1. Create a lecture with content that matches multiple themes
2. Process the lecture
3. Check the recommended problems page
4. Verify all scores are ≤ 100%

Example test transcript:
```
Today we'll discuss calculus fundamentals. We start with derivatives, 
then integration, and finally some applications in physics and engineering.
We'll also touch on limits and continuity.
```

Expected: Multiple high-scoring problems but none > 100%

### Manual Testing for Geometry Topic Mapping

1. Create a lecture with pure geometry content mentioning vectors
2. Process the lecture
3. Check that themes are mapped to "Geometry" not "Linear Algebra"

Example test transcript:
```
Today we'll study vectors in the plane. A vector is a directed line segment 
with magnitude and direction. We can represent points in 2D space using 
coordinate vectors (x, y). The distance between two points can be calculated 
using the distance formula. We'll also learn about the dot product for finding 
angles between vectors and perpendicular vectors.
```

Expected themes:
- ✓ "Vectors" → mapped to "Geometry" (or similar geometry topic)
- ✗ NOT "Linear Algebra"

Counter-example (should map to Linear Algebra):
```
Today we'll study vector spaces and linear transformations. A vector space 
is a set with operations satisfying specific axioms. We'll discuss basis 
vectors, linear independence, and the span of a set. Matrix representations 
of linear transformations and eigenvalues will be covered.
```

Expected themes:
- ✓ "Vector Spaces" or "Linear Transformations" → mapped to "Linear Algebra"

---

## Fine-Tuning Recommendations (Future Work)

While the current fixes significantly improve accuracy, for production use you may want to consider:

### 1. Fine-Tuning the LLM
- Collect examples of lectures with correct topic mappings
- Fine-tune a model like `qwen2.5-math:7b` on your specific domain
- This would give you the best performance for your specific use case

Steps:
1. Collect 50-100 example lectures with manually verified topic mappings
2. Create a fine-tuning dataset in the format expected by your model
3. Use Ollama's fine-tuning capabilities or train externally
4. Test the fine-tuned model against your evaluation set

### 2. Embedding Model Selection
Current model: `bge-base-en-v1.5` (768 dimensions)

Consider trying:
- `bge-large-en-v1.5` - Better semantic understanding (1024 dims - requires schema change)
- `instructor-xl` - Instruction-tuned embeddings
- Math-specific embedding models if available

**Warning**: Changing embedding model requires:
1. Updating `spring.ai.vectorstore.pgvector.dimensions` in config
2. Migrating the database schema
3. Re-indexing all problems (can take hours for large datasets)

### 3. Improved Hybrid Scoring
Current approach combines exact topic match + semantic similarity with fixed weights:
- Exact topic match: 0.8 base score
- Semantic similarity: weighted by theme confidence
- Combined: clamped to 1.0

Potential improvements:
- Learn optimal weights from user feedback
- Implement BM25 for keyword matching
- Add difficulty matching (prefer problems at similar difficulty level)

### 4. Active Learning
Implement feedback collection:
- Ask users to rate problem recommendations
- Track which problems are actually used
- Use this data to improve scoring weights and topic mappings

---

## Summary

The implemented fixes address all reported issues:

✅ **Scores no longer exceed 100%** - Clamping ensures valid percentage display  
✅ **Geometry lectures map correctly** - Enhanced prompts and scoring prevent drift to Linear Algebra  
✅ **Theme extraction errors are now visible** - Detailed error messages help diagnose issues  
✅ **Configuration guidance added** - Users can tune temperature for better results  
✅ **Expanded geometric vocabulary** - Better detection of geometric contexts  
✅ **Comprehensive troubleshooting guide** - `TROUBLESHOOTING.md` provides step-by-step diagnostics

These changes are minimal, surgical modifications that improve the system without requiring fine-tuning or significant architectural changes.

---

## Troubleshooting "No Topics Found"

If you're experiencing issues where lectures don't extract any themes, please refer to the comprehensive `TROUBLESHOOTING.md` guide which covers:

1. **Quick diagnostics**: Check if Ollama is running and the model is available
2. **Common issues**: Connection problems, model availability, response format issues
3. **Step-by-step fixes**: Detailed solutions for each issue type
4. **Model recommendations**: Which models work best for math topic extraction
5. **Configuration tips**: Optimal settings for your use case

**Quick check**:
```bash
# Is Ollama running?
curl http://localhost:11434/api/tags

# Is your model available?
ollama list | grep mistral  # or your configured model

# Check application logs for specific error messages
docker compose logs app | grep -i "theme"
```

**Most common fixes**:
1. Start Ollama: `ollama serve`
2. Pull a reliable model: `ollama pull mistral`
3. Update application.properties: `spring.ai.ollama.chat.options.model=mistral`
4. Restart the application

For detailed diagnostic procedures and model-specific guidance, see `TROUBLESHOOTING.md`.
