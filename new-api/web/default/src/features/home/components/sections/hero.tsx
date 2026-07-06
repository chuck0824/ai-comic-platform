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
import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { HeroTerminalDemo } from '../hero-terminal-demo'

interface HeroProps {
  className?: string
  isAuthenticated?: boolean
}

export function Hero(props: HeroProps) {
  const { t } = useTranslation()

  return (
    <section className='relative z-10 flex min-h-svh items-center overflow-hidden px-6 pt-16 pb-16 md:pt-20 md:pb-24'>
      <div
        aria-hidden
        className='aurora-animate pointer-events-none absolute -inset-[20%] opacity-30 dark:opacity-[0.18]'
        style={{
          background: [
            'radial-gradient(ellipse 80% 60% at 30% 20%, rgba(236,72,153,0.18), transparent 50%)',
            'radial-gradient(ellipse 60% 70% at 70% 40%, rgba(37,99,235,0.16), transparent 50%)',
            'radial-gradient(ellipse 50% 50% at 50% 60%, rgba(99,102,241,0.12), transparent 50%)',
            'radial-gradient(ellipse 40% 40% at 20% 80%, rgba(236,72,153,0.09), transparent 50%)',
          ].join(', '),
        }}
      />
      <div
        aria-hidden
        className='absolute inset-0 -z-10 bg-[linear-gradient(to_right,var(--border)_1px,transparent_1px),linear-gradient(to_bottom,var(--border)_1px,transparent_1px)] [mask-image:radial-gradient(ellipse_60%_50%_at_50%_30%,black_20%,transparent_100%)] bg-[size:4rem_4rem] opacity-[0.06]'
      />

      <div className='mx-auto grid max-w-6xl grid-cols-1 items-start gap-12 lg:grid-cols-12 lg:gap-8'>
        <div className='flex flex-col items-start text-left lg:col-span-6'>
          <div
            className='landing-animate-fade-up mb-5 inline-flex items-center gap-1.5 rounded-full border border-pink-500/20 bg-pink-500/5 px-3 py-1.5 text-[11px] font-medium text-pink-600 opacity-0 shadow-xs dark:border-pink-400/20 dark:bg-pink-400/5 dark:text-pink-400'
            style={{ animationDelay: '0ms' }}
          >
            <span className='relative flex size-1.5'>
              <span className='absolute inline-flex h-full w-full animate-ping rounded-full bg-pink-400 opacity-75' />
              <span className='relative inline-flex size-1.5 rounded-full bg-pink-500 dark:bg-pink-400' />
            </span>
            <span>模型服务 x 漫剧创作</span>
          </div>

          <h1
            className='landing-animate-fade-up text-[clamp(2.25rem,4.5vw,3.25rem)] leading-[1.15] font-bold tracking-tight'
            style={{ animationDelay: '60ms', fontFamily: "'Fredoka', var(--font-sans)" }}
          >
            {t('一个 API 接 40+ 模型，')}
            <br />
            <span className='bg-gradient-to-r from-pink-400 via-indigo-400 to-blue-500 bg-clip-text text-transparent'>
              {t('一条产线出完整作品。')}
            </span>
          </h1>
          <p
            className='landing-animate-fade-up text-muted-foreground/80 mt-5 max-w-xl text-base leading-relaxed opacity-0 md:text-[15px]'
            style={{ animationDelay: '120ms' }}
          >
            {t(
              '左边是模型网关，管接入、路由、计费、配额。右边是漫剧工作台，从故事种子一路跑到可交付成片。同一个账号，两边通用。'
            )}
          </p>

          <div
            className='landing-animate-fade-up mt-8 flex flex-wrap items-center gap-3 opacity-0'
            style={{ animationDelay: '180ms' }}
          >
            <Button
              className='group h-11 rounded-lg px-5 text-sm font-medium transition-shadow hover:shadow-[0_0_28px_rgba(37,99,235,0.25)]'
              render={<a href='http://localhost:3001/dashboard' />}
            >
              {t('进入模型控制台')}
              <ArrowRight className='ml-1.5 size-4 transition-transform duration-200 group-hover:translate-x-0.5' />
            </Button>
            <Button
              className='group h-11 rounded-lg px-5 text-sm font-medium transition-shadow hover:shadow-[0_0_28px_rgba(236,72,153,0.25)]'
              style={{ background: 'linear-gradient(135deg, #EC4899, #6366F1)' }}
              render={<a href='http://localhost:8080/home' />}
            >
              {t('进入漫剧工作台')}
              <ArrowRight className='ml-1.5 size-4 transition-transform duration-200 group-hover:translate-x-0.5' />
            </Button>
          </div>
        </div>

        <div
          className='landing-animate-fade-up flex w-full justify-center opacity-0 lg:col-span-6'
          style={{ animationDelay: '320ms' }}
        >
          <HeroTerminalDemo className='mt-8 lg:mt-0' />
        </div>
      </div>
    </section>
  )
}
