import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';

interface SpeedometerGaugeProps {
  percentage: number;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  showLabel?: boolean;
  showPercentage?: boolean;
  className?: string;
}

const ZONES = [
  { min: 0, max: 20, color: '#EF4444', label: 'Poor' },
  { min: 20, max: 40, color: '#F97316', label: 'Needs Improvement' },
  { min: 40, max: 60, color: '#EAB308', label: 'Average' },
  { min: 60, max: 80, color: '#84CC16', label: 'Good' },
  { min: 80, max: 100, color: '#22C55E', label: 'Excellent' },
];

const MOTIVATIONAL_MESSAGES: Record<string, string> = {
  Poor: "Keep practicing! You'll improve.",
  'Needs Improvement': "Getting there. Don't give up!",
  Average: "Good effort! Keep going.",
  Good: "You're progressing well. Keep going!",
  Excellent: "Excellent work! Outstanding!",
};

const getZone = (percentage: number) => {
  return ZONES.find(zone => percentage >= zone.min && percentage <= zone.max) || ZONES[0];
};

export const SpeedometerGauge = ({
  percentage,
  size = 'md',
  showLabel = true,
  showPercentage = true,
  className,
}: SpeedometerGaugeProps) => {
  const [animatedPercentage, setAnimatedPercentage] = useState(0);
  const [isAnimating, setIsAnimating] = useState(true);

  useEffect(() => {
    setAnimatedPercentage(0);
    setIsAnimating(true);
    
    const duration = 1500;
    const startTime = Date.now();
    
    const animate = () => {
      const elapsed = Date.now() - startTime;
      const progress = Math.min(elapsed / duration, 1);
      
      // Ease-out cubic
      const easeOut = 1 - Math.pow(1 - progress, 3);
      const currentValue = easeOut * percentage;
      
      setAnimatedPercentage(currentValue);
      
      if (progress < 1) {
        requestAnimationFrame(animate);
      } else {
        setIsAnimating(false);
      }
    };
    
    requestAnimationFrame(animate);
  }, [percentage]);

  const zone = getZone(Math.round(animatedPercentage));
  const needleRotation = -90 + (animatedPercentage / 100) * 180;

  const sizes = {
    xs: { width: 100, height: 60, fontSize: 'text-lg', needleLength: 40 },
    sm: { width: 160, height: 90, fontSize: 'text-xl', needleLength: 55 },
    md: { width: 250, height: 135, fontSize: 'text-3xl', needleLength: 85 },
    lg: { width: 320, height: 170, fontSize: 'text-4xl', needleLength: 110 },
  };

  const currentSize = sizes[size];

  // Create arc path for each zone
  const createArc = (startAngle: number, endAngle: number, radius: number, cx: number, cy: number) => {
    const startRad = (startAngle * Math.PI) / 180;
    const endRad = (endAngle * Math.PI) / 180;
    
    const x1 = cx + radius * Math.cos(startRad);
    const y1 = cy + radius * Math.sin(startRad);
    const x2 = cx + radius * Math.cos(endRad);
    const y2 = cy + radius * Math.sin(endRad);
    
    const largeArc = endAngle - startAngle > 180 ? 1 : 0;
    
    return `M ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2}`;
  };

  const cx = 100;
  const cy = 95;
  const outerRadius = 80;
  const innerRadius = 60;
  const strokeWidth = 20;

  return (
    <div className={cn('flex flex-col items-center', className)}>
      <div className="flex items-center gap-4 md:gap-6">
        {/* SVG Gauge */}
        <svg
          viewBox="0 0 200 110"
          style={{ width: currentSize.width, height: currentSize.height }}
          className="drop-shadow-md"
        >
          {/* Background track */}
          <path
            d={createArc(-180, 0, 70, cx, cy)}
            fill="none"
            stroke="hsl(var(--muted))"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
          />

          {/* Zone segments */}
          {ZONES.map((zoneItem, index) => {
            const startAngle = -180 + (zoneItem.min / 100) * 180;
            const endAngle = -180 + (zoneItem.max / 100) * 180;
            return (
              <path
                key={index}
                d={createArc(startAngle, endAngle, 70, cx, cy)}
                fill="none"
                stroke={zoneItem.color}
                strokeWidth={strokeWidth}
                strokeLinecap="butt"
                opacity={0.85}
              />
            );
          })}

          {/* Tick marks */}
          {[0, 20, 40, 60, 80, 100].map((tick) => {
            const angle = (-180 + (tick / 100) * 180) * (Math.PI / 180);
            const x1 = cx + 52 * Math.cos(angle);
            const y1 = cy + 52 * Math.sin(angle);
            const x2 = cx + 58 * Math.cos(angle);
            const y2 = cy + 58 * Math.sin(angle);
            return (
              <line
                key={tick}
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                stroke="hsl(var(--foreground))"
                strokeWidth={2}
                opacity={0.5}
              />
            );
          })}

          {/* Center circle */}
          <circle
            cx={cx}
            cy={cy}
            r={12}
            fill="hsl(var(--card))"
            stroke="hsl(var(--border))"
            strokeWidth={2}
          />
          <circle
            cx={cx}
            cy={cy}
            r={6}
            fill={zone.color}
            className={cn(!isAnimating && 'animate-pulse')}
          />

          {/* Needle */}
          <g
            style={{
              transform: `rotate(${needleRotation}deg)`,
              transformOrigin: `${cx}px ${cy}px`,
              transition: 'none',
            }}
          >
            {/* Needle shadow */}
            <polygon
              points={`${cx},${cy - 4} ${cx + currentSize.needleLength - 15},${cy} ${cx},${cy + 4}`}
              fill="rgba(0,0,0,0.2)"
              transform="translate(2, 2)"
            />
            {/* Needle body */}
            <polygon
              points={`${cx},${cy - 4} ${cx + currentSize.needleLength - 15},${cy} ${cx},${cy + 4}`}
              fill="#1F2937"
            />
            {/* Needle tip */}
            <polygon
              points={`${cx + currentSize.needleLength - 20},${cy - 2} ${cx + currentSize.needleLength - 5},${cy} ${cx + currentSize.needleLength - 20},${cy + 2}`}
              fill="#374151"
            />
          </g>

          {/* Min/Max labels */}
          <text x={25} y={100} fill="hsl(var(--muted-foreground))" fontSize={10} textAnchor="middle">
            0
          </text>
          <text x={175} y={100} fill="hsl(var(--muted-foreground))" fontSize={10} textAnchor="middle">
            100
          </text>
        </svg>

        {/* Percentage display on the right */}
        {showPercentage && (
          <div className="flex flex-col items-start">
            <span
              className={cn(currentSize.fontSize, 'font-bold tabular-nums transition-colors duration-300')}
              style={{ color: zone.color }}
            >
              {Math.round(animatedPercentage)}%
            </span>
            <span className="text-xs text-muted-foreground uppercase tracking-wide">
              {zone.label}
            </span>
          </div>
        )}
      </div>

      {/* Motivational message */}
      {showLabel && (
        <p
          className={cn(
            'text-center mt-3 transition-colors duration-300',
            size === 'sm' ? 'text-sm' : 'text-base md:text-lg',
            'text-muted-foreground'
          )}
        >
          {MOTIVATIONAL_MESSAGES[zone.label]}
        </p>
      )}
    </div>
  );
};
