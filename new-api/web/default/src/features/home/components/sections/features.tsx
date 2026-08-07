/*
Copyright (C) 2023-2026 QuantumNous

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.

For commercial licensing, please contact support@quantumnous.com
*/
import { Zap, Shield, Globe, Code, Gauge, DollarSign, Users, HeartHandshake } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { AnimateInView } from '@/components/animate-in-view'

export function Features() {
  const { t } = useTranslation()
  const items = [
    { id:'unified', num:'01', title: t('home.features.unified.title'), desc: t('home.features.unified.desc'),
      span:'md:col-span-2', icon:<Zap className='size-4 text-pink-400' />,
      visual:(<div className='mt-4 grid grid-cols-3 gap-2'>{['Chat','Responses','Claude','Gemini','DeepSeek','Qwen'].map(n=><div key={n} className='border-border/30 bg-muted/20 text-muted-foreground flex items-center justify-center rounded-lg border px-3 py-2 text-xs transition-all duration-300 hover:border-pink-500/20 hover:bg-pink-500/[0.04] hover:text-foreground'>{n}</div>)}</div>) },
    { id:'routing', num:'02', title: t('home.features.routing.title'), desc: t('home.features.routing.desc'),
      span:'md:col-span-1', icon:<Shield className='size-4 text-emerald-400' />,
      visual:(<div className='mt-4 flex items-center justify-center'><div className='relative'><div className='flex size-16 items-center justify-center rounded-2xl border border-emerald-500/20 bg-emerald-500/5'><Shield className='size-7 text-emerald-500/70' strokeWidth={1.5} /></div><div className='absolute -top-1 -right-1 flex size-4 items-center justify-center rounded-full bg-emerald-500'><svg className='size-2.5 text-white' fill='none' viewBox='0 0 24 24' stroke='currentColor' strokeWidth={3}><path strokeLinecap='round' strokeLinejoin='round' d='m4.5 12.75 6 6 9-13.5'/></svg></div></div></div>) },
    { id:'format', num:'03', title: t('home.features.format.title'), desc: t('home.features.format.desc'),
      span:'md:col-span-1', icon:<Globe className='size-4 text-violet-400' />,
      visual:(<div className='mt-4 space-y-2'>{['OpenAI','Claude','Gemini'].map((s,i)=><div key={s} className='flex items-center gap-2'><div className={`flex size-6 items-center justify-center rounded-full text-[10px] font-bold ${i===1?'border border-blue-500/30 bg-gradient-to-br from-blue-500/20 to-indigo-500/10 text-blue-500':'border-border/40 bg-muted text-muted-foreground border'}`}>{i+1}</div><div className='bg-border/40 h-px flex-1' /><span className='text-muted-foreground text-xs'>{s}</span></div>)}</div>) },
    { id:'cost', num:'04', title: t('home.features.cost.title'), desc: t('home.features.cost.desc'),
      span:'md:col-span-2', icon:<Code className='size-4 text-amber-400' />,
      visual:(<div className='mt-4 flex items-center gap-3'><div className='flex -space-x-2'>{[t('home.features.cost.chip.tokens'),t('home.features.cost.chip.perCall'),t('home.features.cost.chip.cache'),t('home.features.cost.chip.budget')].map(n=><div key={n} className='border-background from-muted to-muted/60 text-muted-foreground flex size-8 items-center justify-center rounded-full border-2 bg-gradient-to-br text-[9px] font-bold'>{n}</div>)}</div><div className='text-muted-foreground flex items-center gap-1.5 text-xs'><DollarSign className='size-3.5 text-blue-500' />{t('home.features.cost.track')}</div></div>) },
  ]
  const extras = [
    { icon:<Gauge className='size-5' strokeWidth={1.5} />, title: t('home.features.extra.concurrency.title'), desc: t('home.features.extra.concurrency.desc') },
    { icon:<DollarSign className='size-5' strokeWidth={1.5} />, title: t('home.features.extra.billing.title'), desc: t('home.features.extra.billing.desc') },
    { icon:<Users className='size-5' strokeWidth={1.5} />, title: t('home.features.extra.tenancy.title'), desc: t('home.features.extra.tenancy.desc') },
    { icon:<HeartHandshake className='size-5' strokeWidth={1.5} />, title: t('home.features.extra.open.title'), desc: t('home.features.extra.open.desc') },
  ]
  return (
    <section className='relative z-10 px-6 py-24 md:py-32'>
      <div className='mx-auto max-w-6xl'>
        <AnimateInView className='mb-16 max-w-lg'>
          <p className='text-muted-foreground mb-3 text-xs font-medium tracking-widest uppercase'>{t('home.features.eyebrow')}</p>
          <h2 className='text-2xl leading-tight font-bold tracking-tight md:text-3xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
            {t('home.features.titleLine1')}
            <br />
            {t('home.features.titleLine2')}
          </h2>
        </AnimateInView>
        <div className='border-border/40 bg-border/40 grid gap-px overflow-hidden rounded-xl border md:grid-cols-3'>
          {items.map((f,i) => (
            <AnimateInView key={f.id} delay={i*100} animation='scale-in'
              className={`bg-background group hover:bg-muted/10 p-7 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_8px_30px_rgba(0,0,0,0.06)] dark:hover:shadow-[0_8px_30px_rgba(0,0,0,0.3)] md:p-8 ${f.span}`}>
              <div className='mb-3 flex items-center gap-3'>
                <span className='border-border/40 bg-muted text-muted-foreground group-hover:border-blue-500/20 group-hover:bg-blue-500/[0.06] group-hover:text-blue-500 flex size-7 items-center justify-center rounded-md border text-[10px] font-semibold tabular-nums transition-colors duration-300'>{f.num}</span>
                <h3 className='text-sm font-semibold'>{f.title}</h3>
              </div>
              <p className='text-muted-foreground text-sm leading-relaxed'>{f.desc}</p>
              {f.visual}
            </AnimateInView>
          ))}
        </div>
        <div className='mt-12 grid grid-cols-2 gap-8 md:grid-cols-4 md:gap-12'>
          {extras.map((f,i) => (
            <AnimateInView key={f.title} delay={i*100} animation='fade-up' className='flex flex-col items-center text-center'>
              <div className='text-muted-foreground border-border/50 bg-muted/30 group-hover:text-foreground hover:border-pink-500/20 mb-3 flex size-12 items-center justify-center rounded-xl border transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_4px_16px_rgba(236,72,153,0.08)]'>{f.icon}</div>
              <h3 className='mb-1.5 text-sm font-semibold'>{f.title}</h3>
              <p className='text-muted-foreground max-w-[200px] text-xs leading-relaxed'>{f.desc}</p>
            </AnimateInView>
          ))}
        </div>
      </div>
    </section>
  )
}
