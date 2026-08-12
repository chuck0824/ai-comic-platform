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
import { openAicpWorkbenchWithSso } from '@/lib/aicp-sso'
import { useComicContent } from '../use-comic-content'

export function ComicFinalCta() {
  const { finalCta } = useComicContent()

  return (
    <section className="comic-final-cta">
      <h2>
        {finalCta.title.includes('，')
          ? <>{finalCta.title.split('，')[0]}，<span>{finalCta.title.split('，').slice(1).join('，')}</span></>
          : <span>{finalCta.title}</span>
        }
      </h2>
      <p>{finalCta.subtitle}</p>
      <Button type="button" onClick={() => void openAicpWorkbenchWithSso('/home')}>
        {finalCta.cta}<ArrowRight aria-hidden />
      </Button>
    </section>
  )
}
