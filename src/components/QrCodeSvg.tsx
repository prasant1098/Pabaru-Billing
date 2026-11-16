import React, { useEffect, useState } from 'react';
import QRCode from 'qrcode';

export async function generateQrDataUrl(value: string): Promise<string | null> {
  try {
    return await QRCode.toDataURL(value, { width: 220, margin: 1 });
  } catch {
    return null;
  }
}

interface QrCodeSvgProps {
  value: string;
  size?: number;
  className?: string;
}

export const QrCodeSvg: React.FC<QrCodeSvgProps> = ({ value, size = 148, className = '' }) => {
  const [dataUrl, setDataUrl] = useState<string>('');

  useEffect(() => {
    let active = true;
    QRCode.toDataURL(value || 'upi://pay', { width: size * 2, margin: 1 })
      .then((url) => {
        if (active) setDataUrl(url);
      })
      .catch(() => {
        if (active) setDataUrl('');
      });
    return () => {
      active = false;
    };
  }, [value, size]);

  if (!dataUrl) {
    return (
      <div
        style={{ width: size, height: size }}
        className={`flex items-center justify-center rounded-lg border border-dashed border-[var(--p-card-border)] bg-[var(--p-secondary-bg)] text-xs text-[var(--p-text-muted)] ${className}`}
      >
        QR
      </div>
    );
  }

  return (
    <img
      src={dataUrl}
      alt="UPI QR Code"
      width={size}
      height={size}
      style={{ width: size, height: size }}
      className={`rounded-lg border border-[var(--p-card-border)] bg-white p-1.5 object-contain ${className}`}
    />
  );
};
