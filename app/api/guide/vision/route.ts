import { google } from '@ai-sdk/google';
import { generateText } from 'ai';

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
      : "Explain what is visible on this screen clearly and concisely.";

    const { text } = await generateText({
      model: google('gemini-2.0-flash'),
      messages: [
        {
          role: 'user',
          content: [
            { type: 'text', text: promptText },
            { 
              type: 'file', 
              mediaType: 'image/jpeg', 
              data: cleanBase64 
            }
          ],
        },
      ],
    });

    return Response.json({ guidance: text });

  } catch (error: any) {
    console.error("Vision Processing Error:", error);
    return Response.json({
      error: 'SERVER_EXCEPTION',
      message: error?.message || 'Unknown server error',
      stack: error?.stack || ''
    }, { status: 500 });
  }
}
