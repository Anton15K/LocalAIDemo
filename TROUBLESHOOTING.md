# Troubleshooting Guide: Theme Extraction Issues

This guide helps diagnose and fix issues when lectures don't extract themes ("no topics found").

## Common Issues and Solutions

### 1. Ollama Service Not Running

**Symptom**: Error message like "Unable to connect to LLM service" or "Connection refused"

**Solution**:
```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# If not running, start Ollama
ollama serve

# In another terminal, verify it's working
ollama list
```

### 2. Model Not Available

**Symptom**: Error message mentioning model not found or "model not available"

**Current Configuration**: The application is configured to use `qwen3-vl:235b-cloud`

**Solution**:
```bash
# Check available models
ollama list

# If qwen3-vl:235b-cloud is not available, pull a working model
ollama pull mistral
# OR
ollama pull llama3
# OR  
ollama pull qwen2.5-math:7b

# Then update application.properties to use the available model:
spring.ai.ollama.chat.options.model=mistral
```

**Recommended Models for Math Topic Classification**:
1. `qwen2.5-math:7b` - Best for mathematical reasoning
2. `mistral` - Good general model, reliable JSON output
3. `llama3` - Strong performance, good at following instructions
4. `qwen2.5:14b` - Excellent for classification tasks

### 3. LLM Response Format Issues

**Symptom**: Error message like "Unable to parse LLM response as JSON"

**Root Cause**: The LLM is not returning valid JSON in the expected format.

**Solution**:

a) **Try a different model**: Some models are better at following JSON format instructions
```bash
# Try mistral (very reliable for JSON)
ollama pull mistral
```
Then update `application.properties`:
```properties
spring.ai.ollama.chat.options.model=mistral
```

b) **Lower the temperature** for more deterministic output:
```properties
spring.ai.ollama.chat.options.temperature=0.3
```

c) **Check the logs**: Look for the raw LLM response in application logs at DEBUG level

### 4. Database Connection Issues

**Symptom**: Error mentioning database connection or "Unable to get available topics"

**Solution**:
```bash
# Check if PostgreSQL is running
docker ps | grep postgres

# If using Docker Compose, check all services
docker compose ps

# View logs
docker compose logs postgres
docker compose logs app
```

### 5. Empty or Very Short Transcripts

**Symptom**: No themes extracted but no error message

**Cause**: Transcript too short or doesn't contain mathematical content

**Solution**: Ensure your transcript:
- Is at least 50 characters long
- Contains clear mathematical terminology
- Is in English (or the language your model supports)

**Example of a good test transcript**:
```
Today we'll study vectors in the plane. A vector is a directed line segment 
with magnitude and direction. We can represent points in 2D space using 
coordinate vectors (x, y). The distance between two points can be calculated 
using the distance formula. We'll also learn about the dot product for finding 
angles between vectors.
```

## Diagnostic Steps

### Step 1: Check Ollama Status
```bash
# Test Ollama connection
curl http://localhost:11434/api/tags

# Should return JSON with list of models
```

### Step 2: Test the Model Directly
```bash
# Test if the model can generate JSON
ollama run mistral '{"prompt": "Return a JSON array with one math topic", "format": "json"}'
```

### Step 3: Check Application Logs

Look for these log entries when processing a lecture:
```
INFO  c.A.L.s.ThemeExtractionService : Extracting themes from transcript of length: XXX
DEBUG c.A.L.s.ThemeExtractionService : Generated prompt for theme extraction (length: XXX chars)
DEBUG c.A.L.s.ThemeExtractionService : LLM response received (length: XXX chars)
DEBUG c.A.L.s.ThemeExtractionService : Attempting to parse JSON response: ...
INFO  c.A.L.s.ThemeExtractionService : Successfully parsed X themes from LLM response
```

If you see:
- `ERROR ... Failed to call LLM`: Ollama connection issue
- `WARN ... LLM returned empty response`: Model issue or prompt too long
- `ERROR ... Failed to parse theme extraction response`: JSON parsing issue

### Step 4: Test with a Simple Lecture

Create a test lecture with simple content:
```
Title: Test Lecture
Transcript: We will study basic algebra. Topics include solving linear equations 
like 2x + 3 = 7, and quadratic equations using the quadratic formula.
```

This should extract at least one theme (Algebra) if everything is working.

### Step 5: Check Configuration

Verify your `application.properties` has:
```properties
# Ollama must be accessible at this URL
spring.ai.ollama.base-url=http://localhost:11434

# Model must be available (check with: ollama list)
spring.ai.ollama.chat.options.model=mistral

# Temperature should be 0.3-0.7 for classification
spring.ai.ollama.chat.options.temperature=0.5

# Logging should be at DEBUG to see detailed errors
logging.level.com.Anton15K.LocalAIDemo=DEBUG
logging.level.org.springframework.ai=DEBUG
```

## Quick Fix Checklist

- [ ] Ollama is running (`ollama serve`)
- [ ] Required model is downloaded (`ollama list` shows your model)
- [ ] Application can reach Ollama (`curl http://localhost:11434/api/tags`)
- [ ] Database is running (`docker compose ps`)
- [ ] Transcript is at least 50 characters with math content
- [ ] Logging level is DEBUG in application.properties
- [ ] Temperature is between 0.3-0.7

## Still Having Issues?

1. **Check the lecture detail page** for the specific error message
2. **Look at application logs** for detailed error information
3. **Try the simplest possible setup**:
   ```bash
   ollama pull mistral
   # Update application.properties to use: model=mistral
   # Test with a simple algebra transcript
   ```
4. **Verify model compatibility**: Not all models support structured JSON output well

## Model-Specific Notes

### mistral
- ✅ Excellent JSON format compliance
- ✅ Good at following instructions
- ⚠️ May be less specialized for math than qwen2.5-math

### llama3
- ✅ Strong general performance
- ✅ Good at structured output
- ⚠️ Larger model, needs more RAM

### qwen2.5-math:7b
- ✅ Specialized for mathematical reasoning
- ✅ Best topic classification for math
- ⚠️ Less commonly available

### qwen3-vl:235b-cloud
- ⚠️ Very large model (235B parameters)
- ⚠️ May not be available in standard Ollama
- 💡 Consider switching to a standard model

## Performance Tips

1. **Use a smaller, faster model** for better response times
2. **Lower temperature (0.3-0.5)** for more consistent output
3. **Keep transcripts under 12000 characters** (automatic truncation applied)
4. **Monitor Ollama resource usage** with `docker stats` or system monitor
