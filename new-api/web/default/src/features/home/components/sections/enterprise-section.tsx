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
import { useTranslation } from 'react-i18next'
import { AnimateInView } from '@/components/animate-in-view'

export function EnterpriseSection() {
  const { t } = useTranslation()
  const items = [
    { num:'01', icon:<Building2 className='size-5' strokeWidth={1.5} />, title: t('home.enterprise.org.title'), desc: t('home.enterprise.org.desc') },
    { num:'02', icon:<CheckCircle className='size-5' strokeWidth={1.5} />, title: t('home.enterprise.approval.title'), desc: t('home.enterprise.approval.desc') },
    { num:'03', icon:<BarChart3 className='size-5' strokeWidth={1.5} />, title: t('home.enterprise.budget.title'), desc: t('home.enterprise.budget.desc') },
    { num:'04', icon:<Shield className='size-5' strokeWidth={1.5} />, title: t('home.enterprise.acl.title'), desc: t('home.enterprise.acl.desc') },
    { num:'05', icon:<Search className='size-5' strokeWidth={1.5} />, title: t('home.enterprise.audit.title'), desc: t('home.enterprise.audit.desc') },
  ]
  return (
    <section className='relative z-10 overflow-hidden border-t border-border/40 px-6 py-24 md:py-32'>
      <div aria-hidden className='pointer-events-none absolute inset-0 opacity-[0.06] dark:opacity-[0.08]'
        style={{ background:'radial-gradient(ellipse 60% 50% at 50% 0%, rgba(37,99,235,0.4), transparent 60%)' }} />
      <div className='relative mx-auto max-w-6xl'>
        <AnimateInView className='mb-16 text-center'>
          <p className='text-muted-foreground mb-3 text-xs font-medium tracking-widest uppercase'>{t('home.enterprise.eyebrow')}</p>
          <h2 className='text-2xl leading-tight font-bold tracking-tight md:text-4xl' style={{ fontFamily: "'Fredoka', var(--font-sans)" }}>
            {t('home.enterprise.titleLine1')}
            <br />
            <span className='bg-gradient-to-r from-pink-400 via-indigo-400 to-blue-500 bg-clip-text text-transparent'>{t('home.enterprise.titleHighlight')}</span>
          </h2>
          <p className='text-muted-foreground/80 mx-auto mt-5 max-w-2xl text-sm leading-relaxed md:text-base'>
            {t('home.enterprise.lead')}
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
