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
import { Button } from '@/components/ui/button'
import { AnimateInView } from '@/components/animate-in-view'

interface CTAProps { className?: string; isAuthenticated?: boolean }

export function CTA(props: CTAProps) {
  if (props.isAuthenticated) return null
  return (
    <section className='relative z-10 overflow-hidden px-6 py-24 md:py-32'>
      <div aria-hidden className='aurora-animate pointer-events-none absolute -inset-[20%] opacity-20 dark:opacity-[0.1]'
        style={{ background: [
          'radial-gradient(ellipse 50% 50% at 30% 50%, rgba(236,72,153,0.2), transparent 70%)',
          'radial-gradient(ellipse 40% 40% at 70% 40%, rgba(37,99,235,0.18), transparent 70%)',
          'radial-gradient(ellipse 45% 35% at 50% 70%, rgba(99,102,241,0.12), transparent 70%)',
        ].join(', ') }} />
      <AnimateInView className='mx-auto max-w-2xl text-center' animation='scale-in'>
        <h2 className='text-2xl leading-tight font-bold tracking-tight md:text-4xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
          从模型，<br />
          <span className='bg-gradient-to-r from-pink-400 via-indigo-400 to-blue-500 bg-clip-text text-transparent'>到作品。</span>
        </h2>
        <p className='text-muted-foreground/80 mx-auto mt-5 max-w-md text-sm leading-relaxed md:text-base'>
          选一个入口，开始吧。同一个账号，两边都能用。
        </p>
        <div className='mt-8 flex items-center justify-center gap-3'>
          <Button className='group rounded-lg' render={<a href='http://localhost:8080/home' />}>
            进入漫剧工作台<ArrowRight className='ml-1 size-3.5 transition-transform duration-200 group-hover:translate-x-0.5' />
          </Button>
          <Button variant='outline' className='border-border/50 hover:border-border hover:bg-muted/50 rounded-lg'
            render={<a href='http://localhost:3001/pricing' />}>
            使用模型服务
          </Button>
        </div>
      </AnimateInView>
    </section>
  )
}
