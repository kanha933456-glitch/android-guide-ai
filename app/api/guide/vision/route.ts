import { google } from '@ai-sdk/google'
import { generateText } from 'ai'
import { NextResponse } from 'next/server'

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const image = typeof body?.image === 'string' ? body.image : ''
    const question = typeof body?.question === 'string' ? body.question.slice(0, 1000) : ''
    
    if (!image.startsWith('data:image/')) {
      return NextResponse.json({ error: 'A valid screenshot is required.' }, { status: 400 })
    }
    if (image.length > 6_000_000) {
      return NextResponse.json({ error: 'Screenshot is too large.' }, { status: 413 })
    }

    const userAsk = question.trim() || 'What should I do next on this screen to move forward?'

    const { text } = await generateText({
      // Fixed Model Name: Official Google AI SDK format
      model: google('gemini-2.5-flash', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI, a precise visual screen assistant and helpful companion. You will be shown a screenshot and the user's exact question. Detect the language the user's question is written in and reply ONLY in that same language. If the question is empty, reply in English. Never use JSON, markdown asterisks (**), code fences, or curly braces. Put key UI elements or buttons inside double quotes like "Submit".

      CRITICAL HANDLING LOGIC:
      1. IF THE USER ASKS A GENERAL KNOWLEDGE OR CONTEXT-INDEPENDENT QUESTION (e.g., definitions like "noun kise kahate hain", math problems, facts, general information): Do NOT look for it on the screenshot. Instead, answer their question directly using your general knowledge in a clear, extremely concise manner.
      2. IF THE USER ASKS ABOUT THE SCREEN OR STUCK PROGRESS: Look carefully at the screenshot and answer exactly what the user asked, based on what is visible. Name the exact visible button, label, icon, or text the user should tap or use. Be extremely specific and concise. 

      FORMATTING RULES:
      - If the answer is a single step or a general answer, write it as one or two plain sentences with no numbers.
      - If there are multiple steps, write each as a numbered line (1. 2. 3.), one short sentence each, maximum 3 steps.
      - If the screenshot clearly shows a password field, OTP field, PIN entry, CVV, or payment/card detail input, do not describe or guide on that field — instead say guidance is not available for sensitive fields, in the same language as the question.
      - Do not add greetings, disclaimers, or extra commentary beyond the answer.`,
      messages: [
        { 
          role: 'user', 
          content: [
            { type: 'text', text: userAsk }, 
            { type: 'image', image }
          ] 
        }
      ],
    })

    return NextResponse.json({ guidance: text.trim() })
  } catch (error) {
    console.error('[v0] Vision guide failed:', error)
    return NextResponse.json({ error: 'Visual guidance is temporarily unavailable.' }, { status: 500 })
  }
}
