import { google } from '@ai-sdk/google';
import { generateText } from 'ai';

export const maxDuration = 30;

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { image, question, history } = body;

    if (!image) {
      return Response.json({ error: 'IMAGE_MISSING', message: 'Image parameter is missing' }, { status: 400 });
    }

    const cleanBase64 = image.replace(/^data:image\/\w+;base64,/, '');

    const promptText = question && question.trim().length > 0
      ? question
      : "Detect the main item, question, or task on the background screen and provide direct, actionable help or the answer.";

    const systemPrompt = `You are Guide AI — a smart, concise screen assistant embedded as a floating overlay on Android.

SCREEN UNDERSTANDING:
- The screenshot shows the CURRENT state of the phone screen. Whatever is visible IS already open/active.
- NEVER tell the user to open something that is already open on screen.
- NEVER describe what the screen shows in general terms like "This screen shows..." or "Foreground overlay...".
- Ignore the Guide AI overlay UI completely (the dark panel, buttons, input box). Focus ONLY on the background app.

ANSWERING RULES:
1. Reply in the EXACT same language the user used in their question:
   - Hinglish question → Hinglish answer (Roman script only, no Devanagari mixing)
   - Hindi (Devanagari) question → Pure Hindi (Devanagari) answer
   - English question → Pure English answer
   - Never mix languages or scripts within one response.
2. Be direct and short — under 40 words unless more detail is truly needed.
3. If a quiz/poll/question is visible on screen, identify the correct answer and state it clearly.
4. If user asks what you can help with, describe what you see on the BACKGROUND screen and offer relevant help.
5. Use conversation history to maintain context — if user refers to something mentioned earlier, use that context.

FORMATTING RULES:
6. Use (parentheses) to highlight ONLY the single most important keyword or answer — minimum 2 to 3 maximum 7 to 10 words only. Do NOT wrap every noun or action word.
   - Good: "Install button par tap karo (Install)"
   - Bad: "(Google Play Store) open karein, (PDF Scanner) search karein aur (Install) par click karein"
7. Do NOT start with arrow symbol — that is added by the Android app automatically.
8. No bullet points, no markdown, no dashes, no bold (**).
9. No unnecessary filler like "aap", "please", "kindly", "zaroor".`;

    // Build conversation messages with history
    const conversationMessages: any[] = [];

    // Add previous history turns (text only, no image for old turns)
    if (history && Array.isArray(history) && history.length > 0) {
      for (const turn of history) {
        if (turn.role === 'user') {
          conversationMessages.push({
            role: 'user',
            content: [{ type: 'text', text: turn.content }]
          });
        } else if (turn.role === 'assistant') {
          conversationMessages.push({
            role: 'assistant',
            content: turn.content
          });
        }
      }
    }

    // Add current turn with fresh screenshot
    conversationMessages.push({
      role: 'user',
      content: [
        { type: 'text', text: promptText },
        {
          type: 'file',
          mediaType: 'image/jpeg',
          data: cleanBase64
        }
      ]
    });

    const { text } = await generateText({
      model: google('gemini-3.5-flash-lite'),
      system: systemPrompt,
      messages: conversationMessages,
    });

    return Response.json({ guidance: text });

  } catch (error: any) {
    console.error("Chat Processing Error:", error);
    return Response.json({
      error: 'SERVER_EXCEPTION',
      message: error?.message || 'Unknown server error',
      stack: error?.stack || ''
    }, { status: 500 });
  }
        }
