export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question } = body;

    if (!image) {
      return Response.json({ error: 'IMAGE_MISSING', message: 'Image parameter is missing' }, { status: 400 });
    }

    const cleanBase64 = image.replace(/^data:image\/\w+;base64,/, '');

    const promptText = question && question.trim().length > 0
      ? question
      : "Detect the main item, question, or task on the background screen and provide direct, actionable help or the answer.";

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/interactions",
      {
        method: "POST",
        headers: {
          "x-goog-api-key": process.env.GEMINI_API_KEY || process.env.GOOGLE_GENERATIVE_AI_API_KEY || "",
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: "gemini-3.8-flash",
          input: [
            { type: "text", text: promptText },
            {
              type: "image",
              data: cleanBase64,
              mime_type: "image/jpeg"
            }
          ]
        })
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

    if (!guidance) {
      return Response.json({ error: 'EMPTY_RESPONSE', message: 'Gemini returned empty response' }, { status: 500 });
    }

    return Response.json({ guidance });

  } catch (error: any) {
    console.error("Guide Processing Error:", error);
    return Response.json({
      error: 'SERVER_EXCEPTION',
      message: error?.message || 'Unknown server error'
    }, { status: 500 });
  }
}
