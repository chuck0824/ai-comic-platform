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
import { Settings, Zap, BarChart3 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { AnimateInView } from '@/components/animate-in-view'

export function HowItWorks() {
  const { t } = useTranslation()
  const steps = [
    { num:'1', title: t('home.how.step1.title'), desc: t('home.how.step1.desc'), icon:<Settings className='size-6' strokeWidth={1.5} /> },
    { num:'2', title: t('home.how.step2.title'), desc: t('home.how.step2.desc'), icon:<Zap className='size-6' strokeWidth={1.5} /> },
    { num:'3', title: t('home.how.step3.title'), desc: t('home.how.step3.desc'), icon:<BarChart3 className='size-6' strokeWidth={1.5} /> },
  ]
  return (
    <section className='border-border/40 relative z-10 border-t px-6 py-24 md:py-32'>
      <div className='mx-auto max-w-6xl'>
        <AnimateInView className='mb-16 text-center md:mb-20'>
          <p className='text-muted-foreground mb-3 text-xs font-medium tracking-widest uppercase'>{t('home.how.eyebrow')}</p>
          <h2 className='text-2xl font-bold tracking-tight md:text-3xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>{t('home.how.title')}</h2>
        </AnimateInView>
        <div className='grid gap-8 md:grid-cols-3 md:gap-12'>
          {steps.map((s, i) => (
            <AnimateInView key={s.num} delay={i*150} animation='fade-up' className='relative flex flex-col items-center text-center'>
              <div className='relative mb-6'>
                <div className='text-muted-foreground border-border/50 bg-muted/30 hover:border-pink-500/20 hover:bg-pink-500/[0.04] flex size-16 items-center justify-center rounded-2xl border transition-all duration-300'>{s.icon}</div>
                <div className='absolute -top-2 -right-2 flex size-6 items-center justify-center rounded-full bg-gradient-to-br from-pink-500 to-blue-600 text-white text-xs font-bold shadow-[0_0_10px_rgba(236,72,153,0.3)]'>{s.num}</div>
              </div>
              <h3 className='mb-2 text-base font-semibold'>{s.title}</h3>
              <p className='text-muted-foreground max-w-[240px] text-sm leading-relaxed'>{s.desc}</p>
            </AnimateInView>
          ))}
        </div>
      </div>
    </section>
  )
}
