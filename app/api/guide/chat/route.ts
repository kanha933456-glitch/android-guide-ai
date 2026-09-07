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
      model: "gemini-3.8-flash",
      system_instruction: `You are Guide AI — a smart, concise screen assistant embedded as a floating overlay on Android.

SCREEN UNDERSTANDING:
- The screenshot shows the CURRENT state of the phone screen. Whatever is visible IS already open/active.
- NEVER tell the user to open something that is already open on screen.
- Ignore the Guide AI overlay UI completely. Focus ONLY on the background app.

ANSWERING RULES:
1. Reply in the EXACT same language the user used in their question.
   - Hinglish question → Hinglish answer (Roman script only)
   - Hindi (Devanagari) question → Pure Hindi answer
   - English question → Pure English answer
2. Be direct and short — under 40 words.
3. If a quiz/question is visible, give the correct answer directly.

FORMATTING RULES:
4. Use (parentheses) on ONLY the single most important word — max 1 to 2 words.
5. No bullet points, no markdown, no bold (**).
6. Do NOT start with arrow symbol — app adds it automatically.`,
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
      }
    };

    // Agar pichla interaction_id hai to bhejo — Google server history yaad rakhega
    if (previousInteractionId && previousInteractionId.trim().length > 0) {
      requestBody.previous_interaction_id = previousInteractionId;
    }

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/interactions`,
      {
        method: "POST",
        headers: {
          "x-goog-api-key": process.env.GEMINI_API_KEY || process.env.GOOGLE_GENERATIVE_AI_API_KEY || "",
          "Content-Type": "application/json"
        },
        body: JSON.stringify(requestBody)
      }
    );

    const data = await response.json();

    if (!response.ok) {
      return Response.json({
        error: 'GEMINI_ERROR',
        message: data?.error?.message || 'Gemini API error'
      }, { status: response.status });
    }

    // Google ka response — output_text se text nikalo, id save karo
    const guidance = data.output_text || "";
    const interactionId = data.id || "";

    if (!guidance) {
      return Response.json({ error: 'EMPTY_RESPONSE', message: 'Gemini returned empty response' }, { status: 500 });
    }

    return Response.json({ guidance, interactionId });

  } catch (error: any) {
    console.error("Chat Processing Error:", error);
    return Response.json({
      error: 'SERVER_EXCEPTION',
      message: error?.message || 'Unknown server error'
    }, { status: 500 });
  }
}
