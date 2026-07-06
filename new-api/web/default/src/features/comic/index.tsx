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
import { useEffect } from 'react'
import { PublicLayout } from '@/components/layout'
import { Footer } from '@/components/layout/components/footer'
import './comic-product.css'
import { useComicContent } from './use-comic-content'
import { AssetDeliveryConcept } from './concepts/asset-delivery-concept'
import { CanvasProductionConcept } from './concepts/canvas-production-concept'
import { DirectorQualityConcept } from './concepts/director-quality-concept'
import { StorySetupConcept } from './concepts/story-setup-concept'
import { StoryboardConcept } from './concepts/storyboard-concept'
import { ChapterProgressNav } from './components/chapter-progress-nav'
import { ComicFaq } from './components/comic-faq'
import { ComicFinalCta } from './components/comic-final-cta'
import { ComicHero } from './components/comic-hero'
import { ProductionChapter } from './components/production-chapter'

/** Map chapter id → concept component. Concept components are stateless demos — safe to pre-create. */
const CONCEPT_BY_ID: Record<string, React.ReactElement> = {
  story: <StorySetupConcept />,
  storyboard: <StoryboardConcept />,
  'canvas-production': <CanvasProductionConcept />,
  'director-quality': <DirectorQualityConcept />,
  'asset-delivery': <AssetDeliveryConcept />,
}

export function ComicProduct() {
  const { chapters } = useComicContent()

  useEffect(() => {
    document.documentElement.classList.add('comic-scroll-snap')
    return () => document.documentElement.classList.remove('comic-scroll-snap')
  }, [])

  return (
    <PublicLayout showMainContainer={false}>
      <main className="comic-page">
        <ChapterProgressNav />
        <ComicHero />
        {chapters.map((chapter, index) => (
          <ProductionChapter
            key={chapter.id}
            chapter={chapter}
            reverse={index % 2 === 1}
          >
            {CONCEPT_BY_ID[chapter.id]}
          </ProductionChapter>
        ))}
        <ComicFaq />
        <ComicFinalCta />
      </main>
      <Footer />
    </PublicLayout>
  )
}
