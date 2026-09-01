import { GoogleGenerativeAI } from '@google/generative-ai';

export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question } = body;

    if (!image) {
      return Response.json({ error: 'Image parameter missing' }, { status: 400 });
    }

    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_GENERATIVE_AI_API_KEY;
    if (!apiKey) {
      return Response.json({ error: 'API key not configured' }, { status: 500 });
    }

    // Direct Base64 cleanup
    const cleanBase64 = image.replace(/^data:image\/\w+;base64,/, '');

    const genAI = new GoogleGenerativeAI(apiKey);
    // Standard model configuration
    const model = genAI.getGenerativeModel({ model: 'gemini-3.6-flash' });

    const promptText = question && question.trim().length > 0 
      ? question 
      : "Explain what is visible on this screen clearly and concisely.";

    const result = await model.generateContent([
      promptText,
      {
        inlineData: {
          data: cleanBase64,
          mimeType: 'image/jpeg'
        }
      }
    ]);

    const guidance = result.response.text();

    return Response.json({ guidance });

  } catch (error: any) {
    console.error("Vision Processing Error:", error);
    return Response.json(
      { error: "Vision guide failed", details: error?.message || "Internal server error" }, 
      { status: 500 }
    );
  }
}
