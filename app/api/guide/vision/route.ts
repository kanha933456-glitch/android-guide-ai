import { GoogleGenerativeAI } from '@google/generative-ai';

export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question } = body;

    if (!image) {
      return Response.json({ error: 'IMAGE_MISSING', message: 'No image provided' }, { status: 400 });
    }

    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_GENERATIVE_AI_API_KEY;
    if (!apiKey) {
      return Response.json({ error: 'NO_API_KEY', message: 'GEMINI_API_KEY is not set in Vercel' }, { status: 500 });
    }

    const cleanBase64 = image.replace(/^data:image\/\w+;base64,/, '');
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });

    const promptText = question && question.trim().length > 0 ? question : "Explain what is visible on screen";

    const result = await model.generateContent([
      promptText,
      { inlineData: { data: cleanBase64, mimeType: 'image/jpeg' } }
    ]);

    return Response.json({ guidance: result.response.text() });

  } catch (error: any) {
    // Exact error response for debugging
    return Response.json({
      error: 'SERVER_EXCEPTION',
      message: error?.message || 'Unknown server error',
      stack: error?.stack || ''
    }, { status: 500 });
  }
}
