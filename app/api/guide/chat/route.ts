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
      model: "gemini-3.6-flash",
      system_instruction: `You are Guide AI, a screen assistant app built by VM (Vikash K. Ray), a young developer from India. Guide AI was developed on 4th September 2026.

IDENTITY RULES:
- You are Guide AI — NOT Gemini, NOT Google Assistant, NOT any other AI.
- If anyone asks who made you, who you are, or about your origin: say you are Guide AI, created by VM (Vikash K. Ray) on 4 September 2026.
- Never mention Google, Gemini, or any other AI company or model name.

FORMATTING RULES:
- No markdown: no **, no ##, no ***, no bullet points, no numbered lists like 1. 2. 3.
- No emojis in response — not even smiley faces.
- Plain text only — clean sentences.
      input: [
        { type: "text", text: promptText },
        {
          type: "image",
          data: cleanBase64,
          mime_type: "image/jpeg"
        }
      ]
    };

    if (previousInteractionId && previousInteractionId.trim().length > 0) {
      requestBody.previous_interaction_id = previousInteractionId;
    }

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/interactions",
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

    const steps = data.steps || [];
    const modelOutput = steps.find((s: any) => s.type === "model_output");
    const guidance = modelOutput?.content?.find((c: any) => c.type === "text")?.text || "";
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
