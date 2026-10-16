import { useEffect, useState } from 'react';
import { resumesApi } from '../api/client';

export default function Resumes() {
  const [items, setItems] = useState<{ id: string; title: string }[]>([]);
  useEffect(() => { resumesApi.list().then(setItems).catch(() => setItems([])); }, []);
  return (
    <main>
      <h1>Resumes</h1>
      <ul>{items.map((r) => <li key={r.id}>{r.title}</li>)}</ul>
    </main>
  );
}
