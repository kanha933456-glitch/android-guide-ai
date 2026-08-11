'use client'

import { useState } from 'react'
import {
  Activity,
  ArrowRight,
  Bell,
  Check,
  ChevronDown,
  CircleHelp,
  Eye,
  Globe2,
  Headphones,
  Languages,
  LayoutDashboard,
  LockKeyhole,
  Menu,
  Mic,
  MoreHorizontal,
  Play,
  Settings2,
  ShieldCheck,
  Sparkles,
  Volume2,
  X,
} from 'lucide-react'

const languages = ['Hindi', 'English', 'اردو', 'বাংলা']

export default function Page() {
  const [active, setActive] = useState(false)
  const [language, setLanguage] = useState('Hindi')
  const [showLanguages, setShowLanguages] = useState(false)
  const [showSettings, setShowSettings] = useState(false)
  const [analysis, setAnalysis] = useState(false)
  const [voice, setVoice] = useState(true)
  const [guidance, setGuidance] = useState('')
  const [loadingGuidance, setLoadingGuidance] = useState(false)

  async function activateGuide() {
    setActive(true)
    setAnalysis(false)
    setGuidance('')
    setLoadingGuidance(true)
    try {
      const response = await fetch('/api/guide', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ language, screenContext: 'The user is on the Guide AI dashboard and wants to understand how to activate real-time screen help.' }),
      })
      const data = await response.json()
      setGuidance(data.guidance || 'Activate screen access to begin receiving step-by-step help.')
    } catch {
      setGuidance('Connect screen access first, then try activating Guide AI again.')
    } finally {
      setLoadingGuidance(false)
      setAnalysis(true)
    }
  }

  return (
    <main className="min-h-screen overflow-hidden bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 flex-col border-r border-border bg-sidebar px-5 py-6 lg:flex">
        <div className="flex items-center gap-3 px-2">
          <div className="grid size-9 place-items-center rounded-xl bg-primary text-primary-foreground shadow-lg shadow-primary/10">
            <Sparkles className="size-4" />
          </div>
          <div>
            <p className="font-semibold tracking-tight">Guide AI</p>
            <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-muted-foreground">Personal copilot</p>
          </div>
        </div>

        <div className="mt-12 space-y-2">
          <p className="px-3 pb-2 font-mono text-[10px] uppercase tracking-[0.2em] text-muted-foreground">Workspace</p>
          <NavItem icon={<LayoutDashboard />} label="Overview" active />
          <NavItem icon={<Activity />} label="Activity" />
          <NavItem icon={<ShieldCheck />} label="Privacy & safety" />
        </div>

        <div className="mt-auto rounded-2xl border border-border bg-card p-4">
          <div className="mb-3 flex items-center justify-between">
            <div className="grid size-8 place-items-center rounded-lg bg-accent text-primary"><LockKeyhole className="size-4" /></div>
            <span className="rounded-full bg-accent px-2 py-1 font-mono text-[9px] uppercase tracking-widest text-primary">Private</span>
          </div>
          <p className="text-sm font-medium">Your screen stays yours</p>
          <p className="mt-1 text-xs leading-relaxed text-muted-foreground">Only send what you choose. Data is processed securely.</p>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="flex h-20 items-center justify-between border-b border-border px-5 sm:px-8 lg:px-12">
          <button className="rounded-lg p-2 text-muted-foreground hover:bg-muted lg:hidden" aria-label="Open navigation"><Menu className="size-5" /></button>
          <div className="hidden text-sm text-muted-foreground sm:block">Friday, August 8 <span className="mx-2 text-border">/</span> <span className="text-foreground">Good evening</span></div>
          <div className="ml-auto flex items-center gap-2 sm:gap-4">
            <div className="relative">
              <button onClick={() => setShowLanguages(!showLanguages)} className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-xs font-medium hover:bg-muted" aria-expanded={showLanguages}><Languages className="size-4 text-primary" /> {language} <ChevronDown className="size-3 text-muted-foreground" /></button>
              {showLanguages && <div className="absolute right-0 top-11 z-30 w-32 overflow-hidden rounded-xl border border-border bg-popover p-1 shadow-xl">{languages.map((item) => <button key={item} onClick={() => { setLanguage(item); setShowLanguages(false) }} className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-xs hover:bg-muted">{item}{language === item && <Check className="size-3 text-primary" />}</button>)}</div>}
            </div>
            <button className="relative rounded-lg p-2 text-muted-foreground hover:bg-muted" aria-label="Notifications"><Bell className="size-5" /><span className="absolute right-1.5 top-1.5 size-1.5 rounded-full bg-primary" /></button>
            <button onClick={() => setShowSettings(!showSettings)} className="grid size-9 place-items-center rounded-xl border border-border bg-card text-sm font-semibold hover:bg-muted" aria-label="Open settings">AS</button>
          </div>
        </header>

        <section className="mx-auto max-w-7xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
          <div className="mb-10 flex flex-col justify-between gap-6 sm:flex-row sm:items-end">
            <div><p className="mb-3 font-mono text-[10px] uppercase tracking-[0.22em] text-primary">Your quiet digital helper</p><h1 className="text-balance text-3xl font-semibold tracking-tight sm:text-4xl">Get unstuck, <span className="text-muted-foreground">instantly.</span></h1><p className="mt-3 max-w-lg text-sm leading-6 text-muted-foreground">Guide AI sees where you are, understands what&apos;s happening, and gently shows you what to do next.</p></div>
            <div className="flex items-center gap-3 text-xs text-muted-foreground"><span className={`size-2 rounded-full ${active ? 'bg-primary shadow-[0_0_10px_var(--primary)]' : 'bg-muted-foreground/40'}`} />{active ? 'Guide is watching' : 'Guide is paused'}</div>
          </div>

          <div className="grid gap-5 xl:grid-cols-[1.3fr_0.7fr]">
            <div className="relative min-h-[390px] overflow-hidden rounded-3xl border border-border bg-card p-6 sm:p-8">
              <div className="absolute right-0 top-0 h-44 w-44 rounded-full bg-primary/5 blur-3xl" />
              <div className="relative flex h-full flex-col justify-between">
                <div className="flex items-start justify-between"><div><div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-accent text-primary"><Eye className="size-6" /></div><h2 className="text-xl font-semibold">Live screen guidance</h2><p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">Turn on the guide when you&apos;re stuck. We&apos;ll read the context and explain the next step in {language}.</p></div><button className="rounded-lg p-2 text-muted-foreground hover:bg-muted" aria-label="More options"><MoreHorizontal className="size-5" /></button></div>
                <div className="mt-10 rounded-2xl border border-dashed border-border bg-background/60 p-4"><div className="flex items-center gap-3"><div className={`grid size-10 place-items-center rounded-xl ${active ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'}`}>{active ? <Activity className="size-5" /> : <Play className="ml-0.5 size-4" />}</div><div className="flex-1"><p className="text-sm font-medium">{active ? 'Guide AI is ready' : 'Ready when you are'}</p><p className="mt-1 text-xs text-muted-foreground">{active ? (loadingGuidance ? 'Guide AI is thinking…' : analysis ? 'Screen context analyzed just now' : 'Looking at your current screen…') : 'Activate to begin real-time help'}</p></div>{active && <span className="font-mono text-[10px] uppercase tracking-widest text-primary">Live</span>}</div></div>
                <button onClick={activateGuide} className={`mt-5 flex h-12 w-full items-center justify-center gap-2 rounded-xl text-sm font-semibold transition-all ${active ? 'bg-accent text-primary hover:bg-accent/80' : 'bg-primary text-primary-foreground hover:opacity-90'}`}>{active ? <><Check className="size-4" /> Guide active</> : <><Sparkles className="size-4" /> Activate Guide AI <ArrowRight className="size-4" /></>}</button>
              </div>
            </div>

            <div className="rounded-3xl border border-border bg-card p-6 sm:p-8"><div className="mb-8 flex items-center justify-between"><div><p className="font-mono text-[10px] uppercase tracking-[0.18em] text-muted-foreground">Assistant voice</p><h2 className="mt-2 text-xl font-semibold">How should I help?</h2></div><div className="grid size-10 place-items-center rounded-xl bg-accent text-primary"><Headphones className="size-5" /></div></div><div className="space-y-3"><button onClick={() => setVoice(!voice)} className={`flex w-full items-center gap-4 rounded-xl border p-4 text-left transition-colors ${voice ? 'border-primary/40 bg-accent/50' : 'border-border hover:bg-muted'}`}><div className="grid size-9 place-items-center rounded-lg bg-card text-primary"><Volume2 className="size-4" /></div><div className="flex-1"><p className="text-sm font-medium">Speak + show text</p><p className="mt-1 text-xs text-muted-foreground">The most helpful way</p></div><span className={`size-4 rounded-full border-4 ${voice ? 'border-primary bg-card' : 'border-muted-foreground/30'}`} /></button><button onClick={() => setVoice(false)} className={`flex w-full items-center gap-4 rounded-xl border p-4 text-left transition-colors ${!voice ? 'border-primary/40 bg-accent/50' : 'border-border hover:bg-muted'}`}><div className="grid size-9 place-items-center rounded-lg bg-card text-primary"><Bell className="size-4" /></div><div className="flex-1"><p className="text-sm font-medium">Text only</p><p className="mt-1 text-xs text-muted-foreground">Quiet, focused guidance</p></div><span className={`size-4 rounded-full border-4 ${!voice ? 'border-primary bg-card' : 'border-muted-foreground/30'}`} /></button></div><div className="mt-7 flex items-center gap-2 text-xs text-muted-foreground"><Globe2 className="size-3.5 text-primary" /> Answers will be given in <span className="font-medium text-foreground">{language}</span></div></div>
          </div>

          <div className="mt-5 grid gap-5 md:grid-cols-3"><StatCard icon={<ShieldCheck />} label="Privacy first" value="On-device ready" /><StatCard icon={<CircleHelp />} label="Guidance used" value="0 sessions" /><StatCard icon={<Settings2 />} label="Permissions" value="2 to review" /></div>

          {analysis && <div className="mt-5 flex items-start gap-4 rounded-2xl border border-primary/20 bg-accent/40 p-5"><div className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary text-primary-foreground"><Sparkles className="size-4" /></div><div className="flex-1"><div className="flex items-center justify-between gap-3"><p className="text-sm font-semibold">A tip from Guide AI</p><button onClick={() => setAnalysis(false)} className="text-muted-foreground hover:text-foreground" aria-label="Dismiss tip"><X className="size-4" /></button></div><p className="mt-1 whitespace-pre-line text-sm leading-6 text-muted-foreground">{guidance || `I can see your current screen. When you&apos;re ready, I&apos;ll explain the next action in simple ${language}.`}</p><button className="mt-3 text-xs font-semibold text-primary hover:underline">Review permissions <ArrowRight className="ml-1 inline size-3" /></button></div></div>}
        </section>
      </div>
    </main>
  )
}

function NavItem({ icon, label, active = false }: { icon: React.ReactNode; label: string; active?: boolean }) {
  return <button className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${active ? 'bg-accent font-medium text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground'}`}>{icon && <span className="[&>svg]:size-4">{icon}</span>}{label}</button>
}

function StatCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return <div className="flex items-center gap-3 rounded-2xl border border-border bg-card p-4"><div className="grid size-9 place-items-center rounded-xl bg-muted text-muted-foreground [&>svg]:size-4">{icon}</div><div><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 text-sm font-medium">{value}</p></div></div>
}
