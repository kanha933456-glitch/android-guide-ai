import { google } from '@ai-sdk/google';
import { generateText } from 'ai';

export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question, systemInstruction } = body;

    if (!image) {
      return Response.json({ error: 'IMAGE_MISSING', message: 'Image parameter is missing' }, { status: 400 });
    }

    const cleanBase64 = image.replace(/^data:image\/\w+;base64,/, '');

    const promptText = question && question.trim().length > 0 
      ? question 
      : "Detect the main item, question, or task on the background screen and provide direct, actionable help or the answer.";

    // Default system prompt if not passed from client
    const defaultSystem = `You are Guide AI, a direct action assistant.
IMPORTANT RULES:
1. NEVER describe the 'Guide AI' floating overlay UI or your own dialog buttons. Ignore the overlay UI in the screenshot completely.
2. Focus ONLY on the background app content behind the overlay.
3. If user clicks 'ASK ABOUT SCREEN' (empty query), detect what main item/question/game/quiz is on screen and answer it DIRECTLY in under 30 words in Hindi/Hinglish.
4. If there is a question/quiz on screen (like a YouTube Poll), solve it and tell the correct option directly.
5. No useless descriptions like 'This screen shows', 'Foreground overlay', or bullet lines like '---'.`;

    const { text } = await generateText({
      model: google('gemini-1.5-flash'),
      system: systemInstruction || defaultSystem,
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
