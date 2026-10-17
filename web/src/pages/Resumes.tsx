import { useEffect, useState } from 'react';
import { Card, CardContent } from '@resume-builder/ui';
import { resumesApi } from '../api/client';

export default function Resumes() {
  const [items, setItems] = useState<{ id: string; title: string }[]>([]);
  useEffect(() => {
    resumesApi.list().then(setItems).catch(() => setItems([]));
  }, []);
  return (
    <main className="space-y-6">
      <h1 className="font-heading text-2xl font-semibold">Resumes</h1>
      <Card>
        <CardContent>
          <ul className="space-y-1 text-sm">
            {items.map((r) => (
              <li key={r.id}>{r.title}</li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </main>
  );
}
