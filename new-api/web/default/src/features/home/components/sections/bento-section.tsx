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
import { Bot, Package } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { AnimateInView } from '@/components/animate-in-view'

export function BentoSection() {
  const { t } = useTranslation()
  const loopLabels = [
    t('home.bento.loop.understand'),
    t('home.bento.loop.execute'),
    t('home.bento.loop.review'),
    t('home.bento.loop.adopt'),
  ]
  const deliveryItems = [
    { l: t('home.bento.delivery.version.l'), s: t('home.bento.delivery.version.s') },
    { l: t('home.bento.delivery.qa.l'), s: t('home.bento.delivery.qa.s') },
    { l: t('home.bento.delivery.assets.l'), s: t('home.bento.delivery.assets.s') },
    { l: t('home.bento.delivery.handoff.l'), s: t('home.bento.delivery.handoff.s') },
  ]
  return (
    <section className='relative z-10 px-6 py-24 md:py-32'>
      <div className='mx-auto max-w-6xl'>
        <div className='grid gap-6 md:grid-cols-[1.05fr_0.95fr]'>
          <AnimateInView animation='fade-up'
            className='group min-h-[400px] rounded-3xl border border-border bg-card p-10 transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_16px_48px_rgba(0,0,0,0.06)] dark:hover:shadow-[0_16px_48px_rgba(0,0,0,0.35)]'>
            <span className='inline-flex items-center gap-2 rounded-full border border-pink-500/20 bg-pink-500/[0.06] px-3 py-1.5 text-xs font-bold text-pink-600 dark:text-pink-400'>
              <Bot className='size-3.5' />{t('home.bento.agentBadge')}
            </span>
            <h3 className='mt-5 text-2xl leading-tight font-bold tracking-tight md:text-3xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
              {t('home.bento.agentTitleLine1')}
              <br />
              {t('home.bento.agentTitleLine2')}
            </h3>
            <p className='text-muted-foreground mt-3 max-w-md text-sm leading-relaxed'>
              {t('home.bento.agentDesc')}
            </p>
            <div className='mt-10 flex gap-2'>
              {loopLabels.map(l => (
                <div key={l} className='flex-1 rounded-xl border border-blue-500/12 bg-blue-500/[0.04] py-4 text-center text-sm font-semibold transition-all duration-300 hover:-translate-y-1 hover:border-blue-500/25 hover:bg-blue-500/[0.08] hover:shadow-[0_8px_24px_rgba(37,99,235,0.08)]'>{l}</div>
              ))}
            </div>
          </AnimateInView>
          <AnimateInView delay={100} animation='fade-up'
            className='group min-h-[400px] rounded-3xl border border-indigo-500/15 bg-gradient-to-br from-[#1a1f35] via-[#141b2e] to-[#0f1627] p-10 text-white transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_16px_48px_rgba(99,102,241,0.15)]'>
            <span className='inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/[0.06] px-3 py-1.5 text-xs font-bold text-white/70'>
              <Package className='size-3.5' />{t('home.bento.deliveryBadge')}
            </span>
            <h3 className='mt-5 text-2xl leading-tight font-bold tracking-tight md:text-3xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
              {t('home.bento.deliveryTitleLine1')}
              <br />
              {t('home.bento.deliveryTitleLine2')}
            </h3>
            <p className='mt-3 max-w-md text-sm leading-relaxed text-white/60'>
              {t('home.bento.deliveryDesc')}
            </p>
            <div className='mt-8 grid grid-cols-2 gap-3'>
              {deliveryItems.map(m => (
                <div key={m.l} className='rounded-xl border border-white/[0.08] bg-white/[0.04] p-4 transition-all duration-300 hover:-translate-y-0.5 hover:bg-white/[0.08]'>
                  <div className='text-lg font-bold'>{m.l}</div><div className='mt-1 text-[10px] text-white/40'>{m.s}</div>
                </div>
              ))}
            </div>
          </AnimateInView>
        </div>
      </div>
    </section>
  )
}
