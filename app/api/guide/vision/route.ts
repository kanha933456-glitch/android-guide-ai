import { google } from '@ai-sdk/google';
import { generateText } from 'ai';

export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question } = body;

    if (!image) {
      return Response.json({ error: 'Image is required' }, { status: 400 });
    }

    // Base64 cleanup
    const cleanImage = image.startsWith('data:') 
      ? image 
      : `data:image/jpeg;base64,${image}`;

    const promptText = question && question.trim().length > 0 
      ? question 
      : "Explain what is on this screen briefly.";

    // Vercel AI SDK Modern Format
    const { text } = await generateText({
      model: google('gemini-1.5-flash'),
      messages: [
        {
          role: 'user',
          content: [
            { type: 'text', text: promptText },
            { type: 'image', image: cleanImage }
          ],
        },
      ],
    });

    return Response.json({ guidance: text });

  } catch (error: any) {
    console.error("Vision guide failed:", error);
    return Response.json(
      { error: "Vision guide failed", details: error?.message || "Unknown error" }, 
      { status: 500 }
    );
  }
}
