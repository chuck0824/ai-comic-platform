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
import { Building2, CheckCircle, BarChart3, Shield, Search } from 'lucide-react'
import { AnimateInView } from '@/components/animate-in-view'

export function EnterpriseSection() {
  const items = [
    { num:'01', icon:<Building2 className='size-5' strokeWidth={1.5} />, title:'组织与成员', desc:'团队、空间、角色分工，一人一权限。' },
    { num:'02', icon:<CheckCircle className='size-5' strokeWidth={1.5} />, title:'统一审批', desc:'关键生产动作留痕，谁批的、什么时候批的，可查。' },
    { num:'03', icon:<BarChart3 className='size-5' strokeWidth={1.5} />, title:'预算用量', desc:'项目和模型维度成本可见，花钱心里有数。' },
    { num:'04', icon:<Shield className='size-5' strokeWidth={1.5} />, title:'权限隔离', desc:'资产和数据边界清晰，该看的看，不该看的碰不到。' },
    { num:'05', icon:<Search className='size-5' strokeWidth={1.5} />, title:'审计追溯', desc:'操作、版本、交付，全程有记录，合规不慌。' },
  ]
  return (
    <section className='relative z-10 overflow-hidden border-t border-border/40 px-6 py-24 md:py-32'>
      <div aria-hidden className='pointer-events-none absolute inset-0 opacity-[0.06] dark:opacity-[0.08]'
        style={{ background:'radial-gradient(ellipse 60% 50% at 50% 0%, rgba(37,99,235,0.4), transparent 60%)' }} />
      <div className='relative mx-auto max-w-6xl'>
        <AnimateInView className='mb-16 text-center'>
          <p className='text-muted-foreground mb-3 text-xs font-medium tracking-widest uppercase'>企业级管理</p>
          <h2 className='text-2xl leading-tight font-bold tracking-tight md:text-4xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
            让一支团队，<br />
            <span className='bg-gradient-to-r from-pink-400 via-indigo-400 to-blue-500 bg-clip-text text-transparent'>像一个系统运转。</span>
          </h2>
          <p className='text-muted-foreground/80 mx-auto mt-5 max-w-2xl text-sm leading-relaxed md:text-base'>
            创作项目、企业治理、模型用量，汇到一个控制面。不管团队是 5 个人还是 500 个人，管理方式一样。
          </p>
        </AnimateInView>
        <div className='grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5'>
          {items.map((item,i) => (
            <AnimateInView key={item.num} delay={i*80} animation='fade-up'
              className='group rounded-2xl border border-border bg-card/60 p-6 backdrop-blur-sm transition-all duration-300 hover:-translate-y-1.5 hover:border-blue-500/20 hover:bg-card hover:shadow-[0_12px_36px_rgba(0,0,0,0.06)] dark:hover:shadow-[0_12px_36px_rgba(0,0,0,0.3)]'>
              <div className='mb-6 text-xs font-bold text-blue-500'>{item.num}</div>
              <div className='text-muted-foreground group-hover:text-foreground mb-2 transition-colors'>{item.icon}</div>
              <h3 className='mb-1 text-sm font-semibold'>{item.title}</h3>
              <p className='text-muted-foreground text-xs leading-relaxed'>{item.desc}</p>
            </AnimateInView>
          ))}
        </div>
      </div>
    </section>
  )
}
