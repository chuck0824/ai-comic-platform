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
import { FileText, Layout, Video, Store } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { AnimateInView } from '@/components/animate-in-view'

export function CreationWorkflow() {
  const { t } = useTranslation()
  const steps = [
    { num:'01', icon:<FileText className='size-5' strokeWidth={1.5} />, title: t('home.workflow.script.title'), desc: t('home.workflow.script.desc'), color:'from-amber-500/20 to-amber-500/5 border-amber-500/20', iconColor:'text-amber-400' },
    { num:'02', icon:<Layout className='size-5' strokeWidth={1.5} />, title: t('home.workflow.canvas.title'), desc: t('home.workflow.canvas.desc'), color:'from-blue-500/20 to-blue-500/5 border-blue-500/20', iconColor:'text-blue-400' },
    { num:'03', icon:<Video className='size-5' strokeWidth={1.5} />, title: t('home.workflow.director.title'), desc: t('home.workflow.director.desc'), color:'from-violet-500/20 to-violet-500/5 border-violet-500/20', iconColor:'text-violet-400' },
    { num:'04', icon:<Store className='size-5' strokeWidth={1.5} />, title: t('home.workflow.market.title'), desc: t('home.workflow.market.desc'), color:'from-emerald-500/20 to-emerald-500/5 border-emerald-500/20', iconColor:'text-emerald-400' },
  ]
  return (
    <section className='relative z-10 border-t border-border/40 bg-gradient-to-b from-indigo-500/[0.02] to-transparent px-6 py-24 md:py-32'>
      <div className='mx-auto max-w-6xl'>
        <AnimateInView className='mb-16 text-center'>
          <p className='text-muted-foreground mb-3 text-xs font-medium tracking-widest uppercase'>{t('home.workflow.eyebrow')}</p>
          <h2 className='text-2xl leading-tight font-bold tracking-tight md:text-4xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
            {t('home.workflow.titleLine1')}
            <br />
            <span className='bg-gradient-to-r from-pink-400 via-indigo-400 to-blue-500 bg-clip-text text-transparent'>{t('home.workflow.titleHighlight')}</span>
          </h2>
          <p className='text-muted-foreground/80 mx-auto mt-5 max-w-2xl text-sm leading-relaxed md:text-base'>
            {t('home.workflow.lead')}
          </p>
        </AnimateInView>
        <div className='grid gap-6 sm:grid-cols-2 lg:grid-cols-4'>
          {steps.map((s, i) => (
            <AnimateInView key={s.num} delay={i*120} animation='fade-up'
              className={`group rounded-2xl border bg-gradient-to-br ${s.color} p-6 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_12px_40px_rgba(0,0,0,0.08)] dark:hover:shadow-[0_12px_40px_rgba(0,0,0,0.3)]`}>
              <div className='mb-4 flex items-center justify-between'><div className={s.iconColor}>{s.icon}</div><span className='text-muted-foreground/40 text-lg font-bold tabular-nums'>{s.num}</span></div>
              <h3 className='mb-2 text-base font-semibold'>{s.title}</h3>
              <p className='text-muted-foreground text-sm leading-relaxed'>{s.desc}</p>
            </AnimateInView>
          ))}
        </div>
      </div>
    </section>
  )
}
