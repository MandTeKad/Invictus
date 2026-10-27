package com.invictus.warrior

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ── Campaign Dashboard ─────────────────────────────────────── */

@Composable
fun CampaignScreen(
    goals: List<GoalDefinition>,
    goalProgress: List<GoalProgress>,
    milestoneProgress: List<MilestoneProgress>,
    currentRank: RankGrant?,
    totalXP: Int,
    campaignDao: CampaignDao,
    scope: CoroutineScope,
    onGoalSelected: (String) -> Unit
) {
    val progressMap = remember(goalProgress) { goalProgress.associateBy { it.goalId } }
    val milestoneMap = remember(milestoneProgress) { milestoneProgress.groupBy { it.goalId } }

    // Domain stats
    val domainStats = remember(goals, progressMap, milestoneMap) {
        CampaignRanks.DOMAINS.map { domain ->
            val domainGoals = goals.filter { it.domain == domain }
            val totalMs = domainGoals.sumOf { g ->
                milestoneMap[g.id]?.size ?: 0
            }
            val achievedMs = domainGoals.sumOf { g ->
                milestoneMap[g.id]?.count { it.status == "achieved" } ?: 0
            }
            DomainStat(domain, domainGoals.size, totalMs, achievedMs)
        }
    }

    val rankLevel = currentRank?.rankLevel ?: 0
    val rankDef = CampaignRanks.rankAt(rankLevel)
    val totalCoreMilestones = milestoneProgress.count { mp ->
        goals.find { g -> milestoneMap[g.id]?.any { it.milestoneId == mp.milestoneId } == true }
            ?.let { it.tier in listOf("core", "core-proposed") } == true
    }
    val achievedCoreMilestones = milestoneProgress.count { mp ->
        mp.status == "achieved" && goals.find { g ->
            milestoneMap[g.id]?.any { it.milestoneId == mp.milestoneId } == true
        }?.let { it.tier in listOf("core", "core-proposed") } == true
    }

    // Simpler core counting
    val coreGoalIds = goals.filter { it.tier in listOf("core", "core-proposed") }.map { it.id }.toSet()
    val totalCore = milestoneProgress.count { it.goalId in coreGoalIds }
    val achievedCore = milestoneProgress.count { it.status == "achieved" && it.goalId in coreGoalIds }

    val imperatorPct = CampaignRanks.imperatorCompletion(achievedCore, totalCore)
    val nextRankProgress = CampaignRanks.progressToNextRank(rankLevel, totalXP, achievedCore, totalCore)

    var selectedDomain by remember { mutableStateOf<String?>(null) }

    ScreenTitle("Campaign", "Imperator Registry")

    // ── Rank & XP card ──
    InvCard(elevated = true, goldBorder = true) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank insignia circle
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Inv.DeepBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(rankDef.insignia, fontSize = 32.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rankDef.name.uppercase(),
                    color = Inv.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    rankDef.title,
                    color = Inv.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$totalXP XP",
                    color = Inv.GoldHighlight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Next rank progress
        if (rankLevel < 9) {
            val nextRank = CampaignRanks.rankAt(rankLevel + 1)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Next: ${nextRank.name}",
                    color = Inv.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(nextRankProgress * 100).toInt()}%",
                    color = Inv.Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            CampaignProgressBar(nextRankProgress, Inv.Gold)

            if (nextRank.requiresGate) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gate: ${nextRank.gateDescription}",
                    color = Inv.TextTertiary,
                    fontSize = 11.sp
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                "IMPERATOR — Campaign Complete",
                color = Inv.Gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // ── Imperator Completion card ──
    InvCard {
        CardLabel("Imperator Progress")
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BigValue("${(imperatorPct * 100).toInt()}%", Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$achievedCore / $totalCore",
                    color = Inv.TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "core milestones",
                    color = Inv.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
        CampaignProgressBar(imperatorPct, Inv.Success)
    }

    // ── Domain Progress ──
    InvCard {
        CardLabel("Domain Progress")
        domainStats.forEach { stat ->
            val pct = if (stat.totalMilestones > 0)
                stat.achievedMilestones.toFloat() / stat.totalMilestones else 0f
            val isSelected = selectedDomain == stat.domain

            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedDomain = if (isSelected) null else stat.domain
                    }
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        CampaignRanks.domainAbbrev(stat.domain),
                        color = Inv.Gold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        stat.domain,
                        color = if (isSelected) Inv.GoldHighlight else Inv.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${stat.achievedMilestones}/${stat.totalMilestones}",
                        color = Inv.TextTertiary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                CampaignProgressBar(pct, domainColor(stat.domain))
            }
        }
    }

    // ── Goals list (filtered by domain if selected) ──
    val displayGoals = if (selectedDomain != null)
        goals.filter { it.domain == selectedDomain }
    else goals

    if (displayGoals.isNotEmpty()) {
        InvCard {
            CardLabel(if (selectedDomain != null) "$selectedDomain Goals" else "All Goals (${goals.size})")

            displayGoals.forEach { goal ->
                val gp = progressMap[goal.id]
                val ms = milestoneMap[goal.id] ?: emptyList()
                val achievedMs = ms.count { it.status == "achieved" }
                val statusColor = statusColor(gp?.status ?: "unassessed")

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onGoalSelected(goal.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicator
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            goal.goal,
                            color = Inv.TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row {
                            Text(
                                goal.id,
                                color = Inv.TextTertiary,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            if (goal.tier == "prestige") {
                                Text(
                                    "PRESTIGE",
                                    color = Inv.GoldMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Milestone progress chips
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 1..4) {
                            val mStatus = ms.find { it.milestoneId == "${goal.id}-M$i" }?.status ?: "unassessed"
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(statusColor(mStatus))
                            )
                        }
                    }
                }

                if (goal != displayGoals.last()) {
                    HorizontalDivider(color = Inv.Border.copy(alpha = 0.3f))
                }
            }
        }
    }
}

/* ── Goal Detail Screen ─────────────────────────────────────── */

@Composable
fun GoalDetailScreen(
    goalId: String,
    campaignDao: CampaignDao,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    var goal by remember { mutableStateOf<GoalDefinition?>(null) }
    var goalProg by remember { mutableStateOf<GoalProgress?>(null) }
    var milestones by remember { mutableStateOf<List<MilestoneDefinition>>(emptyList()) }
    var milestoneProg by remember { mutableStateOf<List<MilestoneProgress>>(emptyList()) }

    LaunchedEffect(goalId) {
        goal = campaignDao.goal(goalId)
        goalProg = campaignDao.goalProgress(goalId)
        milestones = campaignDao.milestonesForGoalSync(goalId)
        milestoneProg = campaignDao.milestoneProgressForGoalSync(goalId)
    }

    val g = goal ?: return
    val gp = goalProg

    // Back button
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onBack() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("← ", color = Inv.Gold, fontSize = 18.sp)
        Text("Back to Campaign", color = Inv.Gold, fontSize = 14.sp)
    }

    ScreenTitle(g.goal)

    // ── Goal info card ──
    InvCard(elevated = true, goldBorder = g.tier == "prestige") {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                CardLabel(g.domain)
                Spacer(Modifier.height(4.dp))
                Text(g.id, color = Inv.TextTertiary, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Row {
                    StatusChip(gp?.status ?: "unassessed")
                    if (g.tier == "prestige") {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PRESTIGE",
                            color = Inv.GoldMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Inv.GoldMuted.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (g.tier == "core-proposed") {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PROPOSED",
                            color = Inv.TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Inv.TextTertiary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (g.verification.isNotBlank()) {
            Text("Verification: ${g.verification}", color = Inv.TextTertiary, fontSize = 12.sp)
        }
        if (g.provenance.isNotBlank()) {
            Text("Source: ${g.provenance}", color = Inv.TextTertiary, fontSize = 11.sp)
        }
    }

    // ── Milestone progression tree ──
    InvCard {
        CardLabel("Progression Stages")

        milestones.forEachIndexed { index, milestone ->
            val mp = milestoneProg.find { it.milestoneId == milestone.id }
            val status = mp?.status ?: "unassessed"
            val isAchieved = status == "achieved"
            val isLast = index == milestones.lastIndex

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Stage indicator column with connecting line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(40.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAchieved) Inv.Success
                                else if (status == "in_progress") Inv.Gold
                                else Inv.Border
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isAchieved) "✓" else "${milestone.stage}",
                            color = if (isAchieved) Inv.DeepBackground else Inv.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isLast) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(
                                    if (isAchieved) Inv.Success.copy(alpha = 0.5f) else Inv.Border.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Stage ${milestone.stage}",
                        color = Inv.Gold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        milestone.stageLabel,
                        color = Inv.TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusChip(status)

                    // Evidence notes
                    if (mp != null && mp.evidenceNotes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Notes: ${mp.evidenceNotes}",
                            color = Inv.TextTertiary,
                            fontSize = 12.sp
                        )
                    }

                    // Action buttons
                    Spacer(Modifier.height(6.dp))
                    MilestoneActions(
                        milestone = milestone,
                        currentStatus = status,
                        campaignDao = campaignDao,
                        scope = scope,
                        onUpdated = {
                            scope.launch {
                                milestoneProg = campaignDao.milestoneProgressForGoalSync(goalId)
                                goalProg = campaignDao.goalProgress(goalId)
                            }
                        }
                    )
                }
            }

            if (!isLast) {
                HorizontalDivider(color = Inv.Border.copy(alpha = 0.15f))
            }
        }
    }

    // ── Goal-level actions ──
    InvCard {
        CardLabel("Goal Status")
        val currentGoalStatus = gp?.status ?: "unassessed"

        // Show note editor
        var noteText by remember(gp) { mutableStateOf(gp?.notes ?: "") }
        var showNotes by remember { mutableStateOf(false) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentGoalStatus != "achieved") {
                InvOutlinedButton(
                    text = "Mark Achieved",
                    onClick = {
                        scope.launch {
                            campaignDao.updateGoalStatus(goalId, "achieved")
                            goalProg = campaignDao.goalProgress(goalId)
                            CampaignRepository.checkAndGrantPromotion(campaignDao)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            InvOutlinedButton(
                text = if (showNotes) "Hide Notes" else "Notes",
                onClick = { showNotes = !showNotes },
                modifier = Modifier.weight(1f)
            )
        }

        if (showNotes) {
            Spacer(Modifier.height(8.dp))
            InvField(
                value = noteText,
                onValueChange = { noteText = it },
                label = "Goal notes",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            InvButton(
                text = "Save Notes",
                onClick = {
                    scope.launch {
                        campaignDao.updateGoalNotes(goalId, noteText)
                        goalProg = campaignDao.goalProgress(goalId)
                    }
                }
            )
        }
    }
}

/* ── Milestone action buttons ───────────────────────────────── */

@Composable
private fun MilestoneActions(
    milestone: MilestoneDefinition,
    currentStatus: String,
    campaignDao: CampaignDao,
    scope: CoroutineScope,
    onUpdated: () -> Unit
) {
    var showEvidence by remember { mutableStateOf(false) }
    var evidenceText by remember { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (currentStatus) {
            "unassessed", "not_started" -> {
                SmallActionButton("Start") {
                    scope.launch {
                        campaignDao.updateMilestoneStatus(milestone.id, "in_progress")
                        onUpdated()
                    }
                }
                SmallActionButton("Skip") {
                    scope.launch {
                        campaignDao.updateMilestoneStatus(milestone.id, "waived")
                        onUpdated()
                    }
                }
            }
            "in_progress" -> {
                SmallActionButton("Complete") {
                    showEvidence = true
                }
                SmallActionButton("Reset") {
                    scope.launch {
                        campaignDao.updateMilestoneStatus(milestone.id, "not_started")
                        onUpdated()
                    }
                }
            }
            "achieved" -> {
                Text("✓ Completed", color = Inv.Success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            "waived" -> {
                SmallActionButton("Reactivate") {
                    scope.launch {
                        campaignDao.updateMilestoneStatus(milestone.id, "not_started")
                        onUpdated()
                    }
                }
                Text("Waived", color = Inv.TextTertiary, fontSize = 12.sp)
            }
        }
    }

    if (showEvidence) {
        Spacer(Modifier.height(6.dp))
        InvField(
            value = evidenceText,
            onValueChange = { evidenceText = it },
            label = "Evidence / notes (optional)",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InvButton(
                text = "Confirm Complete",
                onClick = {
                    scope.launch {
                        campaignDao.updateMilestoneStatus(
                            milestone.id, "achieved", evidenceText
                        )
                        CampaignRepository.awardMilestoneXP(campaignDao, milestone.id, milestone.stage)
                        CampaignRepository.checkAndGrantPromotion(campaignDao)
                        showEvidence = false
                        evidenceText = ""
                        onUpdated()
                    }
                }
            )
            InvOutlinedButton(
                text = "Cancel",
                onClick = {
                    showEvidence = false
                    evidenceText = ""
                }
            )
        }
    }
}

/* ── Shared components ──────────────────────────────────────── */

@Composable
private fun SmallActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Inv.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Inv.TextSecondary),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(text, fontSize = 11.sp)
    }
}

@Composable
fun StatusChip(status: String) {
    val color = statusColor(status)
    val label = when (status) {
        "unassessed" -> "Unassessed"
        "not_started" -> "Not Started"
        "in_progress" -> "In Progress"
        "achieved" -> "Achieved"
        "needs_verification" -> "Needs Verification"
        "waived" -> "Waived"
        else -> status
    }
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun CampaignProgressBar(progress: Float, color: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val cr = CornerRadius(4.dp.toPx())
        // Background track
        drawRoundRect(
            color = Color(0xFF3A3743),
            cornerRadius = cr
        )
        // Filled portion
        if (progress > 0f) {
            drawRoundRect(
                color = color,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = cr
            )
        }
    }
}

/* ── Utility ────────────────────────────────────────────────── */

private data class DomainStat(
    val domain: String,
    val goalCount: Int,
    val totalMilestones: Int,
    val achievedMilestones: Int
)

private fun statusColor(status: String): Color = when (status) {
    "achieved" -> Inv.Success
    "in_progress" -> Inv.Gold
    "not_started" -> Inv.TextTertiary
    "needs_verification" -> Color(0xFFE0A840)
    "waived" -> Inv.Error.copy(alpha = 0.6f)
    else -> Inv.Border // unassessed
}

private fun domainColor(domain: String): Color = when (domain) {
    "Physique" -> Color(0xFF7BAFD4)
    "Strength" -> Color(0xFFD47B7B)
    "Calisthenics & Acrobatics" -> Color(0xFFD4B37B)
    "Combat Unarmed" -> Color(0xFFD49A7B)
    "Combat Armed" -> Color(0xFF9A7BD4)
    "Speed & Endurance" -> Color(0xFF7BD4A3)
    "Aquatic" -> Color(0xFF7BC5D4)
    "Climbing & Wilderness" -> Color(0xFF8FD47B)
    "Resilience & Versatility" -> Color(0xFFD47BAF)
    "Prestige" -> Inv.Gold
    else -> Inv.TextTertiary
}
