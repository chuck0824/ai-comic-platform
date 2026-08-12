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
import { useTranslation } from 'react-i18next'
import {
  COMIC_CHAPTER_KEYS,
  COMIC_FAQ_KEYS,
  HERO_CONTENT_KEYS,
  FINAL_CTA_KEYS,
  type ComicChapterId,
} from './comic-content'

export type LocalizedChapter = {
  id: ComicChapterId
  index: string
  eyebrow: string
  title: string
  description: string
  capabilities: string[]
  cta: string
  tone: 'cyan' | 'violet' | 'blue' | 'pink' | 'green'
}

export type LocalizedFaq = { question: string; answer: string }

export function useComicContent() {
  const { t } = useTranslation()

  const chapters: LocalizedChapter[] = COMIC_CHAPTER_KEYS.map((ch) => ({
    id: ch.id,
    index: ch.index,
    eyebrow: t(ch.eyebrowKey),
    title: t(ch.titleKey),
    description: t(ch.descriptionKey),
    capabilities: ch.capabilityKeys.map((k) => t(k)),
    cta: t(ch.ctaKey),
    tone: ch.tone,
  }))

  const faqs: LocalizedFaq[] = COMIC_FAQ_KEYS.map((faq) => ({
    question: t(faq.questionKey),
    answer: t(faq.answerKey),
  }))

  const hero = {
    eyebrow: t(HERO_CONTENT_KEYS.eyebrowKey),
    titleLine1: t(HERO_CONTENT_KEYS.titleLine1Key),
    titleHighlight: t(HERO_CONTENT_KEYS.titleHighlightKey),
    lead: t(HERO_CONTENT_KEYS.leadKey),
    primaryCta: t(HERO_CONTENT_KEYS.primaryCtaKey),
    secondaryCta: t(HERO_CONTENT_KEYS.secondaryCtaKey),
    proof1: t(HERO_CONTENT_KEYS.proof1Key),
    proof2: t(HERO_CONTENT_KEYS.proof2Key),
    proof3: t(HERO_CONTENT_KEYS.proof3Key),
  }

  const finalCta = {
    title: t(FINAL_CTA_KEYS.titleKey),
    subtitle: t(FINAL_CTA_KEYS.subtitleKey),
    cta: t(FINAL_CTA_KEYS.ctaKey),
  }

  return { chapters, faqs, hero, finalCta }
}
