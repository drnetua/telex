// Regenerates __files/models.json: the 500-entry OpenRouter /models fixture behind the e2e fake provider (QG-4).
// Run: node e2e/wiremock/generate-models.mjs
import { writeFileSync } from "node:fs";

const entry = (id, name, inputs, outputs, prompt, completion, context) => ({
  id,
  name,
  context_length: context,
  architecture: { input_modalities: inputs, output_modalities: outputs },
  pricing: { prompt, completion },
});

const data = [
  // The shipped system-profile models. google/gemini-3.1-flash-image is left out on purpose: Balanced then has a
  // model "Not in the catalog" (AC-213).
  entry("google/gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", ["text", "image"], ["text"], "0.0000001", "0.0000004", 1000000),
  entry("google/gemini-3.5-flash", "Gemini 3.5 Flash", ["text", "image"], ["text"], "0.0000003", "0.0000025", 1000000),
  entry("anthropic/claude-sonnet-5", "Claude Sonnet 5", ["text", "image"], ["text"], "0.000003", "0.000015", 200000),
  entry("google/gemini-3.1-flash-lite-image", "Gemini 3.1 Flash Lite Image", ["text", "image"], ["text", "image"], "0.0000001", "0.0000004", 32000),
  entry("google/gemini-3-pro-image", "Gemini 3 Pro Image", ["text", "image"], ["text", "image"], "0.000002", "0.000012", 65000),
  // Never listed: the answer is audio, so it fits no slot.
  entry("test/audio-only", "Test audio only model", ["text"], ["audio"], "0.000001", "0.000001", 8000),
];
const pad = (n) => String(n).padStart(3, "0");
for (let i = 1; i <= 100; i++)
  data.push(entry(`test/vision-${pad(i)}`, `Test vision model ${pad(i)}`, ["text", "image"], ["text"], "0.0000002", "0.0000008", 128000));
for (let i = 1; i <= 30; i++)
  data.push(entry(`test/image-${pad(i)}`, `Test image model ${pad(i)}`, ["text"], ["image"], "0", "0", 4000));
for (let i = 1; data.length < 500; i++) {
  data.push(entry(`test/text-${pad(i)}`, `Test text model ${pad(i)}`, ["text"], ["text"], "0.00000015", "0.0000006", 128000));
}
writeFileSync(new URL("./__files/models.json", import.meta.url), JSON.stringify({ data }));
