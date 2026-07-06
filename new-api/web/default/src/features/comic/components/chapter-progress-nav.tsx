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
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useComicContent } from '../use-comic-content'
import { getComicLayoutMode } from '../comic-layout'

export function ChapterProgressNav() {
  const { t } = useTranslation()
  const { chapters } = useComicContent()
  const [activeId, setActiveId] = useState('comic-top')
  const sections = useMemo(
    () => [{ id: 'comic-top', title: t('comic.nav.overview') }, ...chapters.map((ch) => ({ id: ch.id, title: ch.title }))],
    [t, chapters],
  )

  useEffect(() => {
    if (!('IntersectionObserver' in window)) return
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const { motion } = getComicLayoutMode(window.innerWidth, reducedMotion)
    if (motion) document.documentElement.classList.add('comic-reveal-ready')
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        entry.target.classList.add('comic-section-visible')
        setActiveId(entry.target.id)
      }),
      { threshold: 0.35 },
    )
    for (const { id } of sections) {
      const element = document.querySelector(`#${id}`)
      if (element) observer.observe(element)
    }
    return () => {
      observer.disconnect()
      document.documentElement.classList.remove('comic-reveal-ready')
    }
  }, [sections])

  return (
    <nav className="comic-progress-nav" aria-label="漫剧生产流程章节">
      {sections.map(({ id, title }) => (
        <a key={id} href={`#${id}`} aria-label={title} aria-current={activeId === id ? 'step' : undefined}>
          <span>{title}</span>
        </a>
      ))}
    </nav>
  )
}
