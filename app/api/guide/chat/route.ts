export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question, previousInteractionId } = body;

    if (!image) {
      return Response.json({ error: 'IMAGE_MISSING', message: 'Image parameter is missing' }, { status: 400 });
    }

    const cleanBase64 = image.replace(/^data:image\/\w+;base64,/, '');

    const promptText = question && question.trim().length > 0
      ? question
      : "Detect the main item, question, or task on the background screen and provide direct, actionable help or the answer.";

    const requestBody: any = {
      model: "gemini-3.5-flash-lite",
      system_instruction: `You are Guide AI, a screen assistant app built by VM (Vikash K. Ray), a young developer from India. Guide AI was developed on 4th September 2026.

IDENTITY RULES:
- You are Guide AI — NOT Gemini, NOT Google Assistant, NOT any other AI.
- If anyone asks who made you, who you are, or about your origin: say you are Guide AI, created by VM (Vikash K. Ray) on 4 September 2026.
- Never mention Google, Gemini, or any other AI company or model name.

FORMATTING RULES:
- No markdown: no **, no ##, no ***, no bullet points, no numbered lists like 1. 2. 3.
- No emojis in response — not even smiley faces.
- Plain text only — clean sentences.
- Keep response short medium large and direct.`,
      input: [
        { type: "text", text: promptText },
        {
          type: "image",
          data: cleanBase64,
          mime_type: "image/jpeg"
        }
      ],
      generation_config: {
        thinking_level: "low"
      },
      stream: true
    };

    if (previousInteractionId && previousInteractionId.trim().length > 0) {
      requestBody.previous_interaction_id = previousInteractionId;
    }

    const geminiResponse = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/interactions?alt=sse",
      {
        method: "POST",
        headers: {
          "x-goog-api-key": process.env.GEMINI_API_KEY || process.env.GOOGLE_GENERATIVE_AI_API_KEY || "",
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestBody)
      }
    );

    if (!geminiResponse.ok) {
      const errData = await geminiResponse.json();
      return Response.json({
        error: 'GEMINI_ERROR',
        message: errData?.error?.message || 'Gemini API error'
      }, { status: geminiResponse.status });
    }

    // SSE stream Android ko forward karo
    const encoder = new TextEncoder();
    let interactionId = "";
    let fullText = "";

    const stream = new ReadableStream({
      async start(controller) {
        const reader = geminiResponse.body?.getReader();
        if (!reader) {
          controller.close();
          return;
        }

        const decoder = new TextDecoder();
        let buffer = "";

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop() || "";

            for (const line of lines) {
              if (!line.startsWith("data: ")) continue;
              const jsonStr = line.slice(6).trim();
              if (!jsonStr || jsonStr === "[DONE]") continue;

              try {
                const event = JSON.parse(jsonStr);

                // Interaction ID save karo
                if (event.id && !interactionId) {
                  interactionId = event.id;
                }

                // Text chunk nikalo
                const steps = event.steps || [];
                for (const step of steps) {
                  if (step.type === "step.delta" || step.type === "model_output") {
                    const content = step.content || step.delta?.content || [];
                    for (const c of content) {
                      if (c.type === "text" && c.text) {
                        fullText += c.text;
                        // Chunk Android ko bhejo
                        const chunk = JSON.stringify({ chunk: c.text, interactionId }) + "\n";
                        controller.enqueue(encoder.encode(chunk));
                      }
                    }
                  }
                }
              } catch (e) {
                // Invalid JSON skip karo
              }
            }
          }

          // Stream khatam — final signal bhejo
          const done = JSON.stringify({ done: true, interactionId, fullText }) + "\n";
          controller.enqueue(encoder.encode(done));

        } catch (e) {
          controller.error(e);
        } finally {
          reader.releaseLock();
          controller.close();
        }
      }
    });

    return new Response(stream, {
      headers: {
        "Content-Type": "application/x-ndjson",
        "Transfer-Encoding": "chunked",
        "Cache-Control": "no-cache"
      }
    });

  } catch (error: any) {
    console.error("Chat Processing Error:", error);
    return Response.json({
      error: 'SERVER_EXCEPTION',
      message: error?.message || 'Unknown server error'
    }, { status: 500 });
  }
}
