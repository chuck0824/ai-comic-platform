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
import type { ReactNode } from 'react'
import { ArrowRight } from 'lucide-react'
import type { LocalizedChapter } from '../use-comic-content'
import { openAicpWorkbenchWithSso } from '@/lib/aicp-sso'

type ProductionChapterProps = {
  chapter: LocalizedChapter
  reverse?: boolean
  children: ReactNode
}

export function ProductionChapter({ chapter, reverse = false, children }: ProductionChapterProps) {
  return (
    <section id={chapter.id} data-comic-section className={`comic-chapter comic-tone-${chapter.tone}`}>
      <div className={`comic-chapter-grid ${reverse ? 'comic-chapter-grid-reverse' : ''}`}>
        <div className="comic-chapter-copy" data-index={chapter.index}>
          <p className="comic-kicker">{chapter.index} / {chapter.eyebrow}</p>
          <h2>{chapter.title}</h2>
          <p className="comic-chapter-description">{chapter.description}</p>
          <div className="comic-capabilities">
            {chapter.capabilities.map((capability) => <span key={capability}>{capability}</span>)}
          </div>
          <button
            type="button"
            className="comic-text-link"
            onClick={() => void openAicpWorkbenchWithSso('/home')}
          >
            {chapter.cta}<ArrowRight aria-hidden className="size-4" />
          </button>
        </div>
        <div className="comic-chapter-visual">{children}</div>
      </div>
    </section>
  )
}
