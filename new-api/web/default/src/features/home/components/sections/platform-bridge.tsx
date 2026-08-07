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
import { ArrowRight, Server, Palette } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { AnimateInView } from '@/components/animate-in-view'
import { getNewApiPublicUrl } from '@/lib/product-urls'
import { openAicpWorkbenchWithSso } from '@/lib/aicp-sso'

export function PlatformBridge() {
  const { t } = useTranslation()
  const modelCards = [
    { k: t('home.bridge.modelCard.models.k'), v: t('home.bridge.modelCard.models.v') },
    { k: t('home.bridge.modelCard.routing.k'), v: t('home.bridge.modelCard.routing.v') },
    { k: t('home.bridge.modelCard.usage.k'), v: t('home.bridge.modelCard.usage.v') },
  ]
  const comicCards = [
    { l: t('home.bridge.comicCard.script.l'), s: t('home.bridge.comicCard.script.s') },
    { l: t('home.bridge.comicCard.canvas.l'), s: t('home.bridge.comicCard.canvas.s') },
    { l: t('home.bridge.comicCard.agent.l'), s: t('home.bridge.comicCard.agent.s') },
    { l: t('home.bridge.comicCard.governance.l'), s: t('home.bridge.comicCard.governance.s') },
  ]
  return (
    <section className='relative z-10 px-6 py-24 md:py-32'>
      <div className='mx-auto max-w-6xl'>
        <AnimateInView className='mb-16 text-center'>
          <p className='text-muted-foreground mb-3 text-xs font-medium tracking-widest uppercase'>{t('home.bridge.eyebrow')}</p>
          <h2 className='text-2xl leading-tight font-bold tracking-tight md:text-4xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
            {t('home.bridge.titleLine1')}
            <br />
            <span className='bg-gradient-to-r from-pink-400 via-indigo-400 to-blue-500 bg-clip-text text-transparent'>
              {t('home.bridge.titleHighlight')}
            </span>
          </h2>
          <p className='text-muted-foreground/80 mx-auto mt-5 max-w-2xl text-sm leading-relaxed md:text-base'>
            {t('home.bridge.lead')}
          </p>
        </AnimateInView>
        <div className='grid gap-6 md:grid-cols-2'>
          <AnimateInView delay={100} animation='fade-up'
            className='group relative min-h-[440px] overflow-hidden rounded-3xl border border-blue-500/15 bg-gradient-to-br from-[#1a2744] via-[#162032] to-[#0f1a2e] p-10 text-white transition-all duration-300 hover:-translate-y-1 hover:border-blue-500/30 hover:shadow-[0_24px_64px_rgba(37,99,235,0.15)]'
          >
            <span className='inline-flex items-center gap-2 rounded-full border border-blue-500/18 bg-blue-500/10 px-3 py-1.5 text-xs font-bold text-blue-300'>
              <Server className='size-3.5' />{t('home.bridge.modelBadge')}
            </span>
            <h3 className='mt-5 text-3xl leading-[1.15] font-bold tracking-tight'>{t('home.bridge.modelTitle')}</h3>
            <p className='mt-3 max-w-md text-sm leading-relaxed text-white/60'>
              {t('home.bridge.modelDesc')}
            </p>
            <a href={getNewApiPublicUrl('/dashboard')} className='mt-4 inline-flex items-center gap-1 text-sm font-semibold text-blue-400 transition-colors hover:text-blue-300'>
              {t('home.bridge.modelCta')} <ArrowRight className='size-3.5' />
            </a>
            <div className='mt-8 grid grid-cols-[80px_1fr] overflow-hidden rounded-2xl border border-white/10 bg-black/25 shadow-[0_20px_48px_rgba(0,0,0,0.3)]'>
              <div className='flex flex-col gap-3 border-r border-white/[0.07] p-4'>
                {[1,2,3,4].map(i => <div key={i} className={`h-1.5 rounded-full ${i===1?'w-full bg-blue-400':'w-3/4 bg-white/10'}`} />)}
              </div>
              <div className='p-5'>
                <div className='grid grid-cols-3 gap-2'>
                  {modelCards.map(m => (
                    <div key={m.k} className='rounded-lg bg-white/[0.06] p-3'><div className='text-sm font-semibold'>{m.k}</div><div className='mt-0.5 text-[10px] text-white/40'>{m.v}</div></div>
                  ))}
                </div>
                <div className='mt-3 h-12 rounded-lg border border-white/[0.06] bg-gradient-to-b from-blue-500/15 to-transparent' />
              </div>
            </div>
          </AnimateInView>
          <AnimateInView delay={200} animation='fade-up'
            className='group relative min-h-[440px] overflow-hidden rounded-3xl border border-pink-500/12 bg-gradient-to-br from-[#1e1b38] via-[#1a1930] to-[#14132a] p-10 text-white transition-all duration-300 hover:-translate-y-1 hover:border-pink-500/25 hover:shadow-[0_24px_64px_rgba(236,72,153,0.12)]'
          >
            <span className='inline-flex items-center gap-2 rounded-full border border-pink-500/16 bg-pink-500/8 px-3 py-1.5 text-xs font-bold text-pink-300'>
              <Palette className='size-3.5' />{t('home.bridge.comicBadge')}
            </span>
            <h3 className='mt-5 text-3xl leading-[1.15] font-bold tracking-tight'>{t('home.bridge.comicTitle')}</h3>
            <p className='mt-3 max-w-md text-sm leading-relaxed text-white/60'>
              {t('home.bridge.comicDesc')}
            </p>
            <button
              type='button'
              onClick={() => void openAicpWorkbenchWithSso('/home')}
              className='mt-4 inline-flex items-center gap-1 text-sm font-semibold text-pink-400 transition-colors hover:text-pink-300'
            >
              {t('home.bridge.comicCta')} <ArrowRight className='size-3.5' />
            </button>
            <div className='mt-8 grid grid-cols-2 gap-2'>
              {comicCards.map(m => (
                <div key={m.l} className='rounded-xl border border-white/[0.06] bg-white/[0.04] p-4 transition-all duration-300 hover:-translate-y-0.5 hover:border-pink-500/20 hover:bg-pink-500/[0.06]'>
                  <div className='text-sm font-semibold'>{m.l}</div><div className='mt-1 text-[10px] text-white/40'>{m.s}</div>
                </div>
              ))}
            </div>
          </AnimateInView>
        </div>
      </div>
    </section>
  )
}
